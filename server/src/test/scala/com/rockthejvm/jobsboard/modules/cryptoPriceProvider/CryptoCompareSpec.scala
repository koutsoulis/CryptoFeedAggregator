package com.rockthejvm.jobsboard.modules.cryptoPriceProvider

import org.http4s.client.Client
import org.http4s.client.websocket.WSClientHighLevel
import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSRequest
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Close
import cats.effect.kernel.DeferredSource
import concurrent.duration.DurationInt
import weaver.SimpleIOSuite
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.cryptoCompare.dto.Message.CryptoData
import cats.effect.testkit.TestControl

object CryptoCompareSpec
    extends SimpleIOSuite
// extends weaver.IOSuite with doobie.weaver.IOChecker
    {
  // override type Res = doobie.Transactor[IO]

  // override def sharedResource =
  //   new DoobieTestHelpers(EmbeddedPg.transactorResource).transactorRsIncludingSetup

  val unercoverableError = """
    {"TYPE":"401", "MESSAGE":"UNAUTHORIZED","PARAMETER": "format","INFO":"We only support JSON format with a valid api_key."}
  """

  val forceDisconnectMessage = """
    {"TYPE":"500", "MESSAGE":"FORCE_DISCONNECT"}
  """

  val cryptoDataMessagePart1 =
    """{"TYPE":"5","MARKET":"CCCAGG","FROMSYMBOL":"BTC","TOSYMBOL":"USD","FLAGS":2,"MEDIAN":49735.6637659428,"LASTTRADEID":"217093517","PRICE":497"""

  val cryptoDataMessagePart2 =
    """35.6637659428,"LASTUPDATE":1707832427,"LASTVOLUME":0.02800179,"LASTVOLUMETO":1392.2750404647,"VOLUMEHOUR":2593.3459684,"VOLUMEHOURTO":128681184.345007,"VOLUMEDAY":15494.02153829,"VOLUMEDAYTO":773812157.387051,"VOLUME24HOUR":49236.99141083,"VOLUME24HOURTO":2449291847.091117,"CURRENTSUPPLYMKTCAP":976108705309.5936,"CIRCULATINGSUPPLYMKTCAP":976108705309.5936,"MAXSUPPLYMKTCAP":1044448937935.905}"""

  val cryptoDataMessage = cryptoDataMessagePart1 ++ cryptoDataMessagePart2

  val cryptoDataMessageDeserialized = CryptoData(TYPE = 5, FROMSYMBOL = "BTC", PRICE = 49735.6637659428)

  def mockClient(mockStream: fs2.Stream[IO, WSFrame.Text]) = new WSClientHighLevel[IO] {

    override def connectHighLevel(request: WSRequest): Resource[IO, WSConnectionHighLevel[IO]] =
      Resource.pure {
        new WSConnectionHighLevel {

          override def receiveStream: fs2.Stream[IO, WSDataFrame] = mockStream

          override def closeFrame: DeferredSource[cats.effect.IO, Close] = ???

          override def receive: IO[Option[WSDataFrame]] = ???

          override def send(wsf: WSDataFrame): IO[Unit] = ???

          override def sendMany[G[_$8]: Foldable, A <: WSDataFrame](wsfs: G[A]): IO[Unit] = ???

          override def subprotocol: Option[String] = ???

        }
      }

  }

  test("throw, eventually") {
    val mockStreamConcludingWithUnrecoverable = fs2
      .Stream(
        WSFrame.Text(cryptoDataMessage),
        WSFrame.Text(cryptoDataMessage),
        WSFrame.Text(cryptoDataMessage),
        WSFrame.Text(cryptoDataMessage),
        WSFrame.Text(unercoverableError)
      ).covary[IO]

    TestControl.executeEmbed(
      CryptoCompare
        .apply(mockClient(mockStreamConcludingWithUnrecoverable)).priceBTC.compile.drain.attempt
        .map(eitherResult => expect(eitherResult.isLeft))
    )
  }

  test("successfuly emit a single message received as two websocket frames") {
    val mockStreamOfSingleMessageAsTwoFrames = fs2
      .Stream(
        WSFrame.Text(data = cryptoDataMessagePart1, last = false),
        WSFrame.Text(data = cryptoDataMessagePart2, last = true)
      ).covary[IO]

    TestControl.executeEmbed(
      CryptoCompare
        .apply(mockClient(mockStreamOfSingleMessageAsTwoFrames)).priceBTC.take(1).compile.toList
        .map { data =>
          expect(data.size == 1) &&
          expect(data.forall(_ === CryptoData(TYPE = 5, FROMSYMBOL = "BTC", PRICE = 49735.6637659428)))
        }
    )
  }

  test("reconnect after the websocket connection is closed by the server") {
    val clientWhichEmitsOneMessageAndDisconnects = {
      val mockStreamOfOneMessage = fs2
        .Stream(
          WSFrame.Text(cryptoDataMessage)
        ).covary[IO]

      mockClient(mockStreamOfOneMessage)
    }

    TestControl.executeEmbed(
      CryptoCompare(clientWhichEmitsOneMessageAndDisconnects).priceBTC.take(2).compile.toList.map { messages =>
        expect(messages.size == 2) && expect(messages.forall(_ === cryptoDataMessageDeserialized))
      }
    )
  }

  test(s"wait at least ${CryptoCompare.reconnectWait} before attempting to connect, both the first time and on subsequent reattempts") {
    val clientWhichEmitsOneMessageAndDisconnects = {
      val mockStreamOfOneMessage = fs2
        .Stream(
          WSFrame.Text(cryptoDataMessage)
        ).covary[IO]

      mockClient(mockStreamOfOneMessage)
    }

    TestControl
      .execute(
        CryptoCompare(clientWhichEmitsOneMessageAndDisconnects).priceBTC.take(2).compile.toList
      ).flatMap { control =>
        for {
          _ <- control.tick // needed to reach the first sleep before the first attempt to connect

          sleepTimeBeforeFirstConnectionExpectation <- control
            .nextInterval.map(timeTillConnect => expect(timeTillConnect >= CryptoCompare.reconnectWait))
          _ <- control.advanceAndTick(CryptoCompare.reconnectWait)

          sleepTimeBeforeSecondConnectionExpectation <- control
            .nextInterval.map(timeTillConnect => expect(timeTillConnect >= CryptoCompare.reconnectWait))
          _ <- control.tickAll

          resultExpectation <- control
            .results
            .map { maybeRes =>
              expect(maybeRes.contains(Outcome.Succeeded(List(cryptoDataMessageDeserialized, cryptoDataMessageDeserialized))))
            }
        } yield sleepTimeBeforeFirstConnectionExpectation && sleepTimeBeforeSecondConnectionExpectation && resultExpectation
      }
  }
}

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


object CryptoCompareSpec extends SimpleIOSuite
// extends weaver.IOSuite with doobie.weaver.IOChecker
{
  // override type Res = doobie.Transactor[IO]

  // override def sharedResource =
  //   new DoobieTestHelpers(EmbeddedPg.transactorResource).transactorRsIncludingSetup

  val wsFrames = fs2.Stream(
    WSFrame.Text("asd1"),
    WSFrame.Text("asd2"),
    WSFrame.Text("asd3"),
    WSFrame.Text("asd4"),
    WSFrame.Text("asd5")
  ).covary[IO]
  .metered(1.seconds)
  .evalTap(IO.print)

  def client = new WSClientHighLevel[IO]{

    override def connectHighLevel(request: WSRequest): Resource[IO, WSConnectionHighLevel[IO]] =
      Resource.pure{
        new WSConnectionHighLevel{

          override def receiveStream: fs2.Stream[IO, WSDataFrame] = wsFrames

          override def closeFrame: DeferredSource[cats.effect.IO, Close] = ???

          override def receive: IO[Option[WSDataFrame]] = ???

          override def send(wsf: WSDataFrame): IO[Unit] = ???

          override def sendMany[G[_$8]: Foldable, A <: WSDataFrame](wsfs: G[A]): IO[Unit] = ???

          override def subprotocol: Option[String] = ???

        }
      }


  }

  test("should print forever"){
    CryptoCompare.apply(client).priceBTC.compile.drain.foreverM
  }
}

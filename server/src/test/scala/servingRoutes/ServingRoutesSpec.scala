package servingRoutes

import weaver.SimpleIOSuite
import marketData.MarketDataService
import myMetrics.MyMetrics
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import cats.effect.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import org.http4s.client.testkit.WSTestClient
import marketData.names.FeedName
import marketData.names.FeedName.Candlesticks
import marketData.domain.Candlestick
import fs2.Stream
import names.ExchangeName
import marketData.names.TradePair
import marketData.names.Currency
import org.http4s.client.websocket.WSRequest
import org.http4s.Uri
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import _root_.io.bullet.borer
import _root_.io.bullet.borer.compat.scodec.*
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*

object ServingRoutesSpec extends SimpleIOSuite {
  val testFeedName = Candlesticks(TradePair(base = Currency("BTC"), quote = Currency("ETH")))

  val marketDataServiceStub = MarketDataService.stub[IO](
    streamStub = feedName =>
      feedName match {
        case someFeedName if someFeedName == testFeedName =>
          Stream.emit(Candlestick("whatever", 1, 1, 1, 1).asInstanceOf[feedName.Message])
        case _ => ???
      }
  )

  val clientWrappingServingRoutesUnderTest = MyMetrics.stub.flatMap { case ((_, outgoingConcurrentStreamsGauge, _)) =>
    given Logger[IO] = NoOpLogger.apply
    val wsRoutes = ServingRoutes(
      marketDataServiceByExchange = {
        case ExchangeName.Coinbase => marketDataServiceStub
        case _ => ???
      },
      metricsRegister = outgoingConcurrentStreamsGauge
    ).wsRoutes.map(_.orNotFound)

    WSTestClient.fromHttpWebSocketApp(wsRoutes)
  }

  test("happy path: emits frames when route corresponds to a serving FeedName") {
    val expectationAsResource = for {
      servingRoutesUnderTest <- clientWrappingServingRoutesUnderTest.toResource
      conn <- servingRoutesUnderTest.connectHighLevel(
        request = WSRequest(uri = Uri
          .unsafeFromString(s"${ExchangeName.Coinbase.toString}")
          .withQueryParam("feedName", testFeedName: FeedName[?]))
      )
      _ <- conn.send(WSFrame.Text("")).toResource
      receivedFrame <- conn.receive.getOrRaiseMsg("").toResource
      decodedFrameLoad = receivedFrame match {
        case Binary(data, _) => borer.Cbor.decode(data).to[Candlestick].valueEither
        case _ => Either.left("non binary frame received")
      }
    } yield IO(expect(decodedFrameLoad.isRight))

    expectationAsResource.useEval
  }
}

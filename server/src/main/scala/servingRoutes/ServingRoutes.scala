package servingRoutes

import _root_.io.bullet.borer.Cbor
import _root_.io.bullet.borer.compat.scodec.*
import cats.*
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import fs2.io.net.Network
import marketData.MarketDataService
import marketData.names.FeedName
import marketData.names.TradePair
import myMetrics.MyMetrics
import names.ExchangeName
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.Response
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger

trait ServingRoutes[F[_]: Async: Network] {
  def wsRoutes: WebSocketBuilder2[F] => HttpRoutes[F]
  def httpRoutes: HttpRoutes[F]
}

object ServingRoutes {
  class ServingRoutesLive[F[_]: Async: Network: Logger](
      marketDataServiceByExchange: ExchangeName => MarketDataService[F],
      metricsRegister: MyMetrics.OutgoingConcurrentStreamsGauge[F]
  ) extends ServingRoutes[F] {
    override def wsRoutes: WebSocketBuilder2[F] => HttpRoutes[F] = { wsBuilder =>
      HttpRoutes.of[F] { case GET -> Root / ExchangeName(exchange) :? FeedName.Matcher(feedName) =>
        wsBuilder.build(
          sendReceive = { messagesFromClient =>
            val messagesToClient = marketDataServiceByExchange(exchange)
              .stream(feedName)
              .map(Cbor.encode(_)(using feedName.borerEncoderForMessage).to[scodec.bits.ByteVector].result)
              .map(WebSocketFrame.Binary.apply(_))

            Stream.eval(Logger[F].debug(s"STREAM REQUEST RECEIVED ${feedName.toString()}")) >>
              Stream.bracket(metricsRegister.value.inc(1, exchange -> feedName)) { _ =>
                metricsRegister.value.dec(1, exchange -> feedName)
              } >>
              messagesFromClient
                .zipRight(messagesToClient) // the client dictates via messagesFromClient the rate at which messages are sent to them
            // TODO test this behaviour after refactoring MarketDataService
          }
        )
      }
    }

    override def httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
      case GET -> Root / ExchangeName(exchange) / "activeCurrencyPairs" =>
        marketDataServiceByExchange(exchange).activeCurrencyPairs.map(Response[F]().withEntity[List[TradePair]])
      case GET -> Root => Async[F].pure(Response(Ok))
    }
  }

  def apply[F[_]: Async: Network: Logger](
      marketDataServiceByExchange: ExchangeName => MarketDataService[F],
      metricsRegister: MyMetrics.OutgoingConcurrentStreamsGauge[F]
  ): ServingRoutes[F] = ServingRoutesLive(
    marketDataServiceByExchange,
    metricsRegister
  )
}

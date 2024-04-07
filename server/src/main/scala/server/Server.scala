package server

import metrics.Metrics
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.HttpRoutes
import org.http4s.server.middleware
import marketData.MarketDataService
import marketData.FeedDefinition
import marketData.Currency
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import _root_.io.bullet.borer.Cbor
import _root_.io.bullet.borer.compat.scodec.*
import org.http4s.websocket.WebSocketFrame
import scala.concurrent.duration.Duration
import fs2.io.net.Network
import org.http4s.metrics.prometheus.PrometheusExportService
import fs2.Stream
// import com.comcast.ip4s.Host
import scala.concurrent.duration.DurationInt

trait Server[F[_]: Async]

object Server {
  class ServerLive[F[_]](implicit F: Async[F]) extends Server[F] {}

  // def apply[F[_]: Network](
  //     marketDataService: MarketDataService[F],
  //     metrics: Metrics[F]
  // )(implicit F: Async[F]): Resource[F, Server[F]] = {
  //   http4s
  //     .ember.server.EmberServerBuilder.default[F]
  //     .withIdleTimeout(Duration.Inf)
  //     .withHttpWebSocketApp { wsBuilder =>
  //       val routes = HttpRoutes.of[F] { case GET -> Root / "orderbook" =>
  //         wsBuilder.build(
  //           sendReceive = { messagesFromClient =>
  //             val messagesToClient = marketDataService
  //               .stream(FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC")))
  //               .map(Cbor.encode(_).to[scodec.bits.ByteVector].result)
  //               .map(WebSocketFrame.Binary.apply(_))
  //             messagesFromClient
  //               .noneTerminate
  //               .handleErrorWith { case _ => Stream(Some(org.http4s.websocket.WebSocketFrame.Close()), None).covary }
  //               .unNoneTerminate
  //               .zipRight(messagesToClient)
  //           }
  //           // send = marketDataService
  //           //   .stream(FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC")))
  //           //   .map(Cbor.encode(_).to[scodec.bits.ByteVector].result)
  //           //   .map(WebSocketFrame.Binary.apply(_)),
  //           // receive = _.as(())
  //         )
  //       }

  //       HttpApp {
  //         metrics.register(routes).combineK(metrics.metricsRoute).orNotFound.run
  //       }
  //     }
  //     .build
  //     .as(new ServerLive[F])
  // }

  def apply[F[_]: Network](
      marketDataService: MarketDataService[F],
      metrics: Metrics[F]
  )(implicit F: Async[F]): Resource[F, Server[F]] = {
    http4s
      .ember.server.EmberServerBuilder.default
      .withHttpWebSocketApp { wsBuilder =>
        val routes = HttpRoutes.of[F] { case GET -> Root / "orderbook" =>
          wsBuilder.build(
            sendReceive = { messagesFromClient =>
              val messagesToClient = marketDataService
                .stream(FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC")))
                .map(Cbor.encode(_).to[scodec.bits.ByteVector].result)
                .map(WebSocketFrame.Binary.apply(_))
              messagesFromClient
                // .noneTerminate
                // .handleErrorWith { case _ => Stream(Some(org.http4s.websocket.WebSocketFrame.Close()), None).covary }
                // .unNoneTerminate
                .zipRight(messagesToClient)
            }
          )
        }

        HttpApp {
          metrics.register(routes).combineK(metrics.metricsRoute).orNotFound.run
        }
      }.build
      .as(new ServerLive[F])
  }
}

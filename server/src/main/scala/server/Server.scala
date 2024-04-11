package server

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
import myMetrics.MyMetrics
import names.Exchange

trait Server[F[_]: Async]

object Server {
  class ServerLive[F[_]](implicit F: Async[F]) extends Server[F] {}

  def apply[F[_]: Network](
      marketDataService: MarketDataService[F],
      metricsExporter: MyMetrics.Exporter[F],
      metricsRegister: MyMetrics.Register[F]
  )(implicit F: Async[F]): Resource[F, Server[F]] = {
    http4s
      .ember.server.EmberServerBuilder.default
      .withHttpWebSocketApp { wsBuilder =>
        val routes = HttpRoutes.of[F] { case GET -> Root :? FeedDefinition.Matcher(feedName) =>
          wsBuilder.build(
            sendReceive = { messagesFromClient =>
              val messagesToClient = marketDataService
                .stream(feedName)
                .map(Cbor.encode(_)(using feedName.borerEncoderForMessage).to[scodec.bits.ByteVector].result)
                .map(WebSocketFrame.Binary.apply(_))

              Stream.bracket(metricsRegister.outgoingConcurrentStreams.inc(1, Exchange.Binance -> feedName)) { _ =>
                metricsRegister.outgoingConcurrentStreams.dec(1, Exchange.Binance -> feedName)
              } >>
                messagesFromClient
                  .zipRight(messagesToClient)
            }
          )
        }

        HttpApp {
          routes.combineK(metricsExporter.metricsRoute).orNotFound.run
        }
      }.build
      .as(new ServerLive[F])
  }
}

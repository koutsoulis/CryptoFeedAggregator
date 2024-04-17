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
import org.http4s.Uri.Path.Segment
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
import scala.concurrent.duration.DurationInt
import myMetrics.MyMetrics
import names.Exchange
import org.http4s.Response
import org.http4s.blazecore.util.EntityBodyWriter
import org.http4s.server.middleware.CORS

trait Server[F[_]: Async]

object Server {
  class ServerLive[F[_]](implicit F: Async[F]) extends Server[F] {}

  def apply[F[_]: Async: Network](
      marketDataServiceByExchange: Exchange => MarketDataService[F],
      metricsExporter: MyMetrics.Exporter[F],
      metricsRegister: MyMetrics.Register[F]
  ): Resource[F, Server[F]] = {
    http4s
      .ember.server.EmberServerBuilder.default
      .withHttpWebSocketApp { wsBuilder =>
        val wsRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / Exchange(exchange) :? FeedDefinition.Matcher(feedName) =>
          wsBuilder.build(
            sendReceive = { messagesFromClient =>
              val messagesToClient = marketDataServiceByExchange(exchange)
                .stream(feedName)
                .map(Cbor.encode(_)(using feedName.borerEncoderForMessage).to[scodec.bits.ByteVector].result)
                .map(WebSocketFrame.Binary.apply(_))

              Stream.eval(Async[F].delay(println(s"STREAM REQUEST RECEIVED -------------------------${feedName.toString()}"))) >>
                Stream.bracket(metricsRegister.outgoingConcurrentStreams.inc(1, exchange -> feedName)) { _ =>
                  metricsRegister.outgoingConcurrentStreams.dec(1, exchange -> feedName)
                } >>
                messagesFromClient
                  .zipRight(messagesToClient)
            }
          )
        }

        val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / Exchange(exchange) / "allCurrencyPairs" =>
          Response[F]().withEntity(marketDataServiceByExchange(exchange).allCurrencyPairs).pure[F]
        }

        HttpApp {
          wsRoutes
            .combineK(metricsExporter.metricsRoute)
            // .combineK(httpRoutes)
            .combineK(CORS.policy.withAllowOriginAll.httpRoutes(httpRoutes))
            .orNotFound.run
        }
      }.build
      .as(new ServerLive[F])
  }
}

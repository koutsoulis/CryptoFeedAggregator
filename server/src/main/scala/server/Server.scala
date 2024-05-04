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
import marketData.names.FeedName
import marketData.names.Currency
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
import names.ExchangeName
import org.http4s.Response
import org.http4s.blazecore.util.EntityBodyWriter
import org.http4s.server.middleware.CORS
import marketData.names.TradePair
import org.typelevel.log4cats.Logger
import servingRoutes.ServingRoutes

trait Server

object Server {
  class ServerLive extends Server

  def apply[F[_]: Async: Network: Logger](
      servingRoutes: ServingRoutes[F],
      metricsExporter: MyMetrics.Exporter[F]
  ): Resource[F, Server] = {
    http4s
      .ember.server.EmberServerBuilder.default
      .withHttpWebSocketApp { wsBuilder =>
        HttpApp {
          servingRoutes
            .wsRoutes(wsBuilder)
            .combineK(metricsExporter.metricsRoute)
            .combineK(CORS.policy.withAllowOriginAll.httpRoutes(servingRoutes.httpRoutes))
            .orNotFound.run
        }
      }.build
      .as(new ServerLive)
  }
}

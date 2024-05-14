package server

import cats.*
import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import fs2.io.net.Network
import myMetrics.MyMetrics
import org.http4s
import org.http4s.HttpApp
import org.http4s.implicits.*
import org.http4s.server.middleware
import org.http4s.server.middleware.CORS
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
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(4041).get)
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

package server

import cats.*
import cats.data.*
import cats.effect.*
import cats.syntax.all.*
import config.Config
import fs2.io.net.Network
import myMetrics.MyMetrics
import org.http4s
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Response
import org.http4s.Uri
import org.http4s.dsl.io.*
import org.http4s.headers.Origin
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.Logger
import servingRoutes.ServingRoutes

trait Server

object Server {
  def apply[F[_]: Async: Network: Logger](
      servingRoutes: ServingRoutes[F],
      metricsExporter: MyMetrics.Exporter[F],
      config: Config
  ): Resource[F, Server] = {
    val healthRoute = HttpRoutes.of[F] { case GET -> Root =>
      Async[F].pure(Response(Ok))
    }

    http4s
      .ember.server.EmberServerBuilder.default
      .withHost(config.host)
      .withPort(config.port)
      .withHttpWebSocketApp { wsBuilder =>
        HttpApp {
          servingRoutes
            .wsRoutes(wsBuilder)
            .combineK(metricsExporter.metricsRoute)
            .combineK(healthRoute)
            .combineK(
              CORS
                .policy
                // .withAllowOriginAll
                .withAllowOriginHost(
                  Set(
                    Origin.Host(Uri.Scheme.https, Uri.RegName("app.kotopoulion.xyz"), None),
                    Origin.Host(Uri.Scheme.https, Uri.RegName("poc-app.kotopoulion.xyz"), None)
                  )
                )
                .httpRoutes(servingRoutes.httpRoutes))
            .orNotFound.run
        }
      }.build
      .as(new Server {})
  }
}

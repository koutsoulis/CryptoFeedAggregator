package com.rockthejvm.jobsboard.modules

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import com.rockthejvm.jobsboard.playground.EmberConfig
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import org.http4s.server.Server
import scala.util.chaining.*
import org.http4s.server.middleware
import org.typelevel.log4cats
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.HttpRoutes

object EmberServer {

  def apply[F[_]: Async: log4cats.LoggerFactory](httpApi: HttpApi[F]): Resource[F, Server] =
    Resource.eval(MonadThrow[F].fromEither[EmberConfig](conf)).flatMap { conf =>
      EmberServerBuilder
        .default[F]
        .withHost(conf.host)
        .withPort(conf.port)
        .withHttpApp(errorMiddleware(httpApi.endpoints).orNotFound)
        .build
    }

  // def loggerMiddleware[F[_]: Async]() =
  //   middleware
  //     .Logger
  //     .httpRoutes(
  //       logHeaders = true,
  //       logBody = true,
  //       logAction = Some((msg: String) => Console[F].println(msg))
  //     )

  def errorMiddleware[F[_]: Async: log4cats.LoggerFactory](service: HttpRoutes[F]) =
    middleware
      .ErrorAction
      .httpRoutes[F](
        httpRoutes = service,
        (req, throwable) =>
          log4cats
            .LoggerFactory
            .getLogger
            .error(ctx = Map("sessionId" -> "mockIdValue"), throwable)(throwable.getMessage())
      )

  def conf: Either[Throwable, EmberConfig] =
    ConfigSource
      .resources("application.conf")
      .load[EmberConfig]
      .left
      .map(_.prettyPrint().pipe(new Exception(_)))
}

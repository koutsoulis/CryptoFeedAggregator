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
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.client3.http4s.Http4sBackend
import org.http4s.server.middleware.CORS
import sttp.capabilities.fs2.Fs2Streams

object EmberServer {

  def apply[F[_]: Async: log4cats.LoggerFactory](httpApi: HttpApi[F]): Resource[F, Server] = {
    val swaggerEndpoints: List[sttp.tapir.server.ServerEndpoint[Fs2Streams[F], F]] = SwaggerInterpreter()
      .fromServerEndpoints[F](
        endpoints = List[ServerEndpoint[Fs2Streams[F], F]](httpApi.endpoints),
        title = "title required by swagger",
        version = "version string"
      )

    (
      Http4sBackend.usingDefaultEmberClientBuilder[F](),
      Resource.eval(MonadThrow[F].fromEither[EmberConfig](conf))
    ).parFlatMapN { case (backend, conf) =>
      EmberServerBuilder
        .default[F]
        .withHost(conf.host)
        .withPort(conf.port)
        // .withHttpApp(errorMiddleware(httpApi.endpoints).orNotFound)
        .withHttpApp(
          middlewares(
            Http4sServerInterpreter
              .apply[F]()
              .toRoutes(
                swaggerEndpoints
                  .appended(httpApi.loginRoute)
                  .appended(httpApi.oAuthRedirectRoute(backend))
                  .appended(httpApi.endpoints)
              )
          ).orNotFound
        )
        .build
    }
  }
  // def loggerMiddleware[F[_]: Async]() =
  //   middleware
  //     .Logger
  //     .httpRoutes(
  //       logHeaders = true,
  //       logBody = true,
  //       logAction = Some((msg: String) => Console[F].println(msg))
  //     )

  def middlewares[F[_]: Async: log4cats.LoggerFactory](service: HttpRoutes[F]): HttpRoutes[F] = {

    // temporary; for manual testing requests from front end which is served on :1234 while backend server is on :4041
    // which Single Origin Policy disallows
    def corsMiddleware[F[_]: Async](service: HttpRoutes[F]): HttpRoutes[F] =
      CORS.policy.withAllowOriginAll.httpRoutes[F](service)

    def errorMiddleware[F[_]: Async: log4cats.LoggerFactory](service: HttpRoutes[F]): HttpRoutes[F] =
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

    corsMiddleware[F].compose(errorMiddleware[F]).apply(service)
  }

  def conf: Either[Throwable, EmberConfig] =
    ConfigSource
      .resources("application.conf")
      .load[EmberConfig]
      .left
      .map(_.prettyPrint().pipe(new Exception(_)))
}

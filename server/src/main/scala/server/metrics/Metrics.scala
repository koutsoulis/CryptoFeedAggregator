package server.metrics

// import org.http4s.server.middleware.Metrics
// import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s
import org.http4s.server.middleware
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s.metrics.TerminationType
import org.http4s.Method
import org.http4s.Status
import fs2.io.net.Network
import io.prometheus.client.CollectorRegistry
import org.http4s.HttpRoutes

trait Metrics[F[_]: Sync] {
  def register(input: HttpRoutes[F]): HttpRoutes[F]
  def metricsRoute: HttpRoutes[F]
}

object Metrics {
  def apply[F[_]: Sync]: Resource[F, Metrics[F]] = {
    for {
      registry <- Prometheus.collectorRegistry
      metricsOps <- Prometheus.metricsOps(registry)
    } yield new Metrics[F] {

      override def register(input: HttpRoutes[F]): HttpRoutes[F] = middleware.Metrics(metricsOps)(input)

      override def metricsRoute: HttpRoutes[F] = PrometheusExportService.service(registry)

    }
    // Prometheus
    //   .collectorRegistry.flatMap(
    //     Prometheus.metricsOps(_)
    //   ).map { metricsOps =>
    //     new Metrics[F] {

    //       override def register(input: HttpRoutes[F]): HttpRoutes[F] = middleware.Metrics(metricsOps)(input)

    //       override def metricsRoute: HttpRoutes[F] = PrometheusExportService.service(metricsOps.)

    //     }
    //   }
  }
}

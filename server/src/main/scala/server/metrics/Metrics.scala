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
import org.typelevel.log4cats.slf4j.Slf4jLogger
import prometheus4cats.*
import prometheus4cats.javasimpleclient.JavaMetricRegistry
import org.typelevel.log4cats.Logger
import marketData.FeedDefinition
import marketData.FeedDefinition.OrderbookFeed
import org.typelevel.log4cats.slf4j.Slf4jFactory

trait Metrics[F[_]: Async: Slf4jFactory] {
  def register(input: HttpRoutes[F]): HttpRoutes[F]
  def metricsRoute: HttpRoutes[F]
  def obGauge: Gauge[F, Long, OrderbookFeed]
}

object Metrics {
  def apply[F[_]: Async: Slf4jFactory]: Resource[F, Metrics[F]] = {
    for {
      registry <- Prometheus.collectorRegistry
      metricsOps <- Prometheus.metricsOps(registry)
      logger <- Resource.eval(Slf4jFactory[F].create)
      p4catsJavaMetricRegistry <- JavaMetricRegistry.Builder().withRegistry(registry).build(using Async[F], logger)
      metricsFactory = MetricFactory.builder.build(p4catsJavaMetricRegistry)
      gauge <- metricsFactory
        .gauge("orderbook").ofLong.help("concurrent outgoing orderbook streams")
        .labels(
          Label.Name("OrderbookFeed") -> { (obf: FeedDefinition.OrderbookFeed) =>
            obf.currency1.name ++ obf.currency2.name
          }
        ).build
    } yield new Metrics[F] {
      override def obGauge: Gauge[F, Long, OrderbookFeed] = gauge

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

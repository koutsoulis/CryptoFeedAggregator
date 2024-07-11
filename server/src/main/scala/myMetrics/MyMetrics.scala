package myMetrics

import cats.*
import cats.effect.*
import cats.syntax.all.*
import marketData.names.FeedName
import myMetrics.impl.ConcurrentStreamsGaugeFactory
import names.ExchangeName
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.Prometheus
import org.http4s.metrics.prometheus.PrometheusExportService
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import prometheus4cats.*
import prometheus4cats.javasimpleclient.JavaMetricRegistry
import marketData.names.FeedName.FeedNameQ

object MyMetrics {

  trait Exporter[F[_]: Sync] {
    def metricsRoute: HttpRoutes[F]
  }

  trait OutgoingConcurrentStreamsGauge[F[_]: Async] {
    def value: Gauge[F, Long, (ExchangeName, FeedNameQ)]
  }

  trait IncomingConcurrentStreamsGauge[F[_]: Async] {
    def value: Gauge[F, Long, (ExchangeName, FeedNameQ)]
  }

  private def applyImpl[F[_]: Async: Logger](stubMetricFactory: Option[MetricFactory[F]] = None)
      : Resource[F, (Exporter[F], OutgoingConcurrentStreamsGauge[F], IncomingConcurrentStreamsGauge[F])] = {
    for {
      registry <- Prometheus.collectorRegistry
      metricsFactory <- stubMetricFactory
        .map(Resource.pure)
        .getOrElse(
          JavaMetricRegistry
            .Builder().withRegistry(registry).build
            .map(MetricFactory.builder.build)
        )

      concurrentStreamsGaugeDefinition = new ConcurrentStreamsGaugeFactory[F]
      outgoingConcurrentStreamsGaugeImpl <-
        concurrentStreamsGaugeDefinition.make(
          metricsFactory = metricsFactory,
          name = "concurrent_outgoing_streams",
          helpStringForMetric = "concurrent outgoing streams indexed by Exchange and FeedDefinition"
        )

      incomingConcurrentStreamsGaugeImpl <-
        concurrentStreamsGaugeDefinition.make(
          metricsFactory = metricsFactory,
          name = "concurrent_incoming_streams",
          helpStringForMetric = "number of concurrent streams from Exchange to Server"
        )
    } yield {
      val exporter = new Exporter[F] {

        override val metricsRoute: HttpRoutes[F] = PrometheusExportService.service(registry)

      }

      val outgoingConcurrentStreamsGauge = new OutgoingConcurrentStreamsGauge[F] {

        override val value: Gauge[F, Long, (ExchangeName, FeedNameQ)] = outgoingConcurrentStreamsGaugeImpl

      }

      val incomingConcurrentStreamsGauge = new IncomingConcurrentStreamsGauge[F] {
        override val value: Gauge[F, Long, (ExchangeName, FeedNameQ)] = incomingConcurrentStreamsGaugeImpl
      }

      (exporter, outgoingConcurrentStreamsGauge, incomingConcurrentStreamsGauge)
    }
  }

  def apply[F[_]: Async: Logger]: Resource[F, (Exporter[F], OutgoingConcurrentStreamsGauge[F], IncomingConcurrentStreamsGauge[F])] =
    applyImpl()

  def stub[F[_]: Async]: F[(Exporter[F], OutgoingConcurrentStreamsGauge[F], IncomingConcurrentStreamsGauge[F])] =
    applyImpl(stubMetricFactory = Some(MetricFactory.noop[F]))(using Async[F], NoOpLogger.apply)
      .allocated.map(_._1) // no real need for finalizers e.g. Prometheus.collectorRegistry deregisters collectors, but we dont register any
}

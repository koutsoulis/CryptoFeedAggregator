package myMetrics

import org.http4s
import org.http4s.server.middleware
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s.Method
import org.http4s.Status
import fs2.io.net.Network
import org.http4s.HttpRoutes
import org.typelevel.log4cats.slf4j.Slf4jLogger
import prometheus4cats.*
import prometheus4cats.javasimpleclient.JavaMetricRegistry
import org.typelevel.log4cats.Logger
import marketData.FeedDefinition
import marketData.FeedDefinition.OrderbookFeed
import org.typelevel.log4cats.slf4j.Slf4jFactory
import names.Exchange

object MyMetrics {

  trait Exporter[F[_]] {
    def metricsRoute: HttpRoutes[F]
  }

  trait Register[F[_]] {
    def outgoingConcurrentStreams: Gauge[F, Long, (Exchange, FeedDefinition[?])]
  }

  def apply[F[_]: Async: Slf4jFactory]: Resource[F, (Exporter[F], Register[F])] = {
    for {
      registry <- Prometheus.collectorRegistry
      logger <- Resource.eval(Slf4jFactory[F].create)
      p4catsJavaMetricRegistry <- JavaMetricRegistry.Builder().withRegistry(registry).build(using Async[F], logger)
      metricsFactory = MetricFactory.builder.build(p4catsJavaMetricRegistry)
      outgoingConcurrentStreamsDefinition <- metricsFactory
        .gauge("concurrent_outgoing_streams").ofLong.help("concurrent outgoing streams indexed by Exchange and FeedDefinition")
        .labels[(Exchange, FeedDefinition[?])](
          Label.Name("exchange") -> { case (exchange, _) =>
            exchange.toString()
          },
          Label.Name("feed_definition") -> { case (_, fd) =>
            fd.nameWithoutParametersForPrometheusLabelValue
          },
          Label.Name("parameters") -> { case (_, fd) =>
            fd.parametersStringForPrometheusLabelValue
          }
        ).build
    } yield {
      val exporter = new Exporter[F] {

        override def metricsRoute: HttpRoutes[F] = PrometheusExportService.service(registry)

      }

      val register = new Register[F] {

        override def outgoingConcurrentStreams: Gauge[F, Long, (Exchange, FeedDefinition[?])] = outgoingConcurrentStreamsDefinition

      }

      (exporter, register)
    }
  }
}

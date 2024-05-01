package myMetrics.impl

import prometheus4cats.Gauge.Name
import prometheus4cats.Metric.Help
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import prometheus4cats.MetricFactory
import cats.effect.*
import prometheus4cats.*
import names.ExchangeName
import marketData.names.FeedName

class ConcurrentStreamsGaugeFactory[F[_]] {
  def make(
      metricsFactory: MetricFactory[F],
      name: Name,
      helpStringForMetric: Help): Resource[F, Gauge[F, Long, (ExchangeName, FeedName[?])]] =
    metricsFactory
      .gauge(name).ofLong.help(helpStringForMetric)
      .labels[(ExchangeName, FeedName[?])](
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
}

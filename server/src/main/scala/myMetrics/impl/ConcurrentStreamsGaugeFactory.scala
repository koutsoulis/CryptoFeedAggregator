package myMetrics.impl

import cats.*
import cats.effect.*
import cats.syntax.all.*
import marketData.names.FeedName
import names.ExchangeName
import prometheus4cats.*
import prometheus4cats.Gauge.Name
import prometheus4cats.Metric.Help
import prometheus4cats.MetricFactory
import marketData.names.FeedName.FeedNameQ

class ConcurrentStreamsGaugeFactory[F[_]: Async] {
  def make(
      metricsFactory: MetricFactory[F],
      name: Name,
      helpStringForMetric: Help): Resource[F, Gauge[F, Long, (ExchangeName, FeedNameQ)]] =
    metricsFactory
      .gauge(name).ofLong.help(helpStringForMetric)
      .labels[(ExchangeName, FeedNameQ)](
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

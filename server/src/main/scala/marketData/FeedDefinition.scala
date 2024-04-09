package marketData

import marketData.exchange.impl.binance.domain.Orderbook

// TODO: rename to MarketFeedName
sealed trait FeedDefinition[M] {
  type Message = M

  def nameWithoutParametersForPrometheusLabelValue: String = this.getClass().getSimpleName()

  def parametersStringForPrometheusLabelValue: String
}

object FeedDefinition {
  case class OrderbookFeed(currency1: Currency, currency2: Currency) extends FeedDefinition[Orderbook] {
    override def parametersStringForPrometheusLabelValue: String = currency1.name ++ currency2.name
  }

  case class Stub(_value: Boolean = false) extends FeedDefinition[Stub.Message] {
    override def parametersStringForPrometheusLabelValue: String = "stub"
  }
  object Stub {
    case class Message(value: Int)
  }
}

case class Currency(name: String)

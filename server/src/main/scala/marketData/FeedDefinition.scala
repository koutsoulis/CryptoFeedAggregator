package marketData

import marketData.exchange.impl.binance.domain.Orderbook

sealed trait FeedDefinition[M] {
  type Message = M
}

object FeedDefinition {
  case class OrderbookFeed(currency1: Currency, currency2: Currency) extends FeedDefinition[Orderbook]

  case class Stub(_value: Boolean = false) extends FeedDefinition[Stub.Message]
  object Stub {
    case class Message(value: Int)
  }
}

case class Currency(name: String)

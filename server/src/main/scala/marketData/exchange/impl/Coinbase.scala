package marketData.exchange.impl

import marketData.exchange.ExchangeSpecific
import cats.effect.*
import marketData.names.FeedName
import marketData.names.TradePair
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Candlesticks
import marketData.names.Currency
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*

trait Coinbase[F[_]: Async] extends ExchangeSpecific[F] {}

object Coinbase {
  class CoinbaseLive[F[_]: Async](
      client: coinbase.Client[F]
  ) extends Coinbase {

    override def allCurrencyPairs: List[TradePair] = List(TradePair(Currency("ETH"), Currency("BTC")))

    override def activeCurrencyPairs: F[List[TradePair]] = allCurrencyPairs.pure

    override def stream[M](feedDef: FeedName[M]): fs2.Stream[F, M] = feedDef match {
      case feedName: OrderbookFeed => client.orderbook(feedName.tradePair)
      case Candlesticks(tradePair) => ???
    }

  }

}

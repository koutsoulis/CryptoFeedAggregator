package marketData.exchange

import marketData.Currency
import marketData.TradePair
import marketData.FeedDefinition
import fs2.Stream
import cats.effect.*

trait ExchangeSpecific[F[_]: Async] {
  def allCurrencyPairs: List[(Currency, Currency)]

  def activeCurrencyPairs: F[List[TradePair]]

  def allFeedDefs: List[FeedDefinition[?]] = {
    val allLevel2Defs: List[FeedDefinition.OrderbookFeed] = allCurrencyPairs.map(FeedDefinition.OrderbookFeed.apply)

    allLevel2Defs // plus others
  }

  def stream[M](feedDef: FeedDefinition[M]): Stream[F, M]
}

object ExchangeSpecific {
  def stub[F[_]: Async]: ExchangeSpecific[F] = new ExchangeSpecific[F] {

    override def allCurrencyPairs: List[(Currency, Currency)] = ???

    override def activeCurrencyPairs: F[List[TradePair]] = ???

    override def allFeedDefs: List[FeedDefinition[?]] = List(new FeedDefinition.Stub)

    override def stream[M](feedDef: FeedDefinition[M]): Stream[F, M] = ???

  }
}

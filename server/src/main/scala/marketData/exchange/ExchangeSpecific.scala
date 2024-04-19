package marketData.exchange

import marketData.Currency
import marketData.TradePair
import marketData.FeedName
import fs2.Stream
import cats.effect.*

trait ExchangeSpecific[F[_]: Async] {
  def allCurrencyPairs: List[(Currency, Currency)]

  def activeCurrencyPairs: F[List[TradePair]]

  def allFeedDefs: List[FeedName[?]] = {
    val allLevel2Defs: List[FeedName.OrderbookFeed] = allCurrencyPairs.map(FeedName.OrderbookFeed.apply)

    allLevel2Defs // plus others
  }

  def stream[M](feedDef: FeedName[M]): Stream[F, M]
}

object ExchangeSpecific {
  def stub[F[_]: Async]: ExchangeSpecific[F] = new ExchangeSpecific[F] {

    override def allCurrencyPairs: List[(Currency, Currency)] = ???

    override def activeCurrencyPairs: F[List[TradePair]] = ???

    override def allFeedDefs: List[FeedName[?]] = List(new FeedName.Stub)

    override def stream[M](feedDef: FeedName[M]): Stream[F, M] = ???

  }
}

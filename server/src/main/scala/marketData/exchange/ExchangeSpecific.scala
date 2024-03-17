package marketData.exchange

import marketData.Currency
import marketData.FeedDefinition

trait ExchangeSpecific {
  def allCurrencyPairs: List[(Currency, Currency)]

  def allFeedDefs: List[FeedDefinition[?]] = {
    val allLevel2Defs: List[FeedDefinition.OrderbookFeed] = allCurrencyPairs.map(FeedDefinition.OrderbookFeed.apply)

    allLevel2Defs // plus others
  }
}

object ExchangeSpecific {
  def stub: ExchangeSpecific = new ExchangeSpecific {

    override def allCurrencyPairs: List[(Currency, Currency)] = ???

    override def allFeedDefs: List[FeedDefinition[?]] = List(new FeedDefinition.Stub)

  }
}

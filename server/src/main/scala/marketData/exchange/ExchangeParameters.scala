package marketData.exchange

import marketData.Currency
import marketData.FeedDefinition

trait ExchangeParameters {
  def allCurrencyPairs: List[(Currency, Currency)]

  def allFeedDefs: List[FeedDefinition[?]] = {
    val allLevel2Defs: List[FeedDefinition.Level2] = allCurrencyPairs.map(FeedDefinition.Level2.apply)

    allLevel2Defs // plus others
  }
}

object ExchangeParameters {
  def stub: ExchangeParameters = new ExchangeParameters {

    override def allCurrencyPairs: List[(Currency, Currency)] = ???

    override def allFeedDefs: List[FeedDefinition[?]] = List(new FeedDefinition.Stub)

  }
}

package marketData.exchange

import marketData.Currency
import marketData.FeedDefinition

trait ExchangeParameters {
  def allCurrencyPairs: List[(Currency, Currency)]

  def allFeedDefs: List[FeedDefinition[?]] = {
    val allLevel2Defs: List[FeedDefinition.Level2] = ???

    allLevel2Defs // plus others
  }
}

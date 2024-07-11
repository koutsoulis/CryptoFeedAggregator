package marketData.exchange

import cats.*
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import marketData.names.Currency
import marketData.names.FeedName
import marketData.names.FeedName.Candlesticks
import marketData.names.FeedName.OrderbookFeed
import marketData.names.TradePair
import names.ExchangeName
import marketData.names.FeedName.FeedNameQ

trait Exchange[F[_]: Async] {

  /**
   * @return
   *   All the trade pairs (active or not) the target cryptocurrency exchange is aware of at the time of the server's instantiation
   */
  def allCurrencyPairs: List[TradePair]

  /**
   * @return
   *   The trade pairs actively traded on the exchange (not paused) at the time this is executed
   */
  def activeCurrencyPairs: F[List[TradePair]]

  /**
   * @return
   *   All combinations of (tradePair x FeedName) the Exchange can make sense of
   */
  def allFeedNames: List[FeedNameQ] = {
    val allLevel2Names: List[FeedName.OrderbookFeed] = allCurrencyPairs.map(FeedName.OrderbookFeed.apply)
    val allCandlestickNames: List[FeedName.Candlesticks] = allCurrencyPairs.map(FeedName.Candlesticks.apply)

    allLevel2Names.prependedAll(allCandlestickNames) // plus others
  }

  def stream[M](feedName: FeedName[M]): Stream[F, M]

  def name: ExchangeName
}

object Exchange {
  def stub[F[_]](using F: Async[F])(
      allCurrencyPairsStub: List[TradePair] = List(TradePair(base = Currency("BTC"), quote = Currency("ETH"))),
      activeCurrencyPairsStub: F[List[TradePair]] = List(TradePair(base = Currency("BTC"), quote = Currency("ETH"))).pure[F],
      streamStub: (feedName: FeedNameQ) => Stream[F, feedName.Message] = { _ => Stream.raiseError(new UnsupportedOperationException) },
      nameStub: ExchangeName = ExchangeName.Binance
  ): Exchange[F] = new Exchange {

    override def allCurrencyPairs: List[TradePair] = allCurrencyPairsStub

    override def activeCurrencyPairs: F[List[TradePair]] = activeCurrencyPairsStub

    override def stream[M](feedDef: FeedName[M]): Stream[F, M] = streamStub(feedDef)

    override def name: ExchangeName = nameStub

  }
}

package marketData.exchange

import marketData.names.Currency
import marketData.names.TradePair
import marketData.names.FeedName
import fs2.Stream
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import marketData.domain.Candlestick
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Candlesticks
import names.ExchangeName

trait ExchangeSpecific[F[_]: Async] {
  def allCurrencyPairs: List[TradePair]

  def activeCurrencyPairs: F[List[TradePair]]

  def allFeedNames: List[FeedName[?]] = {
    val allLevel2Names: List[FeedName.OrderbookFeed] = allCurrencyPairs.map(FeedName.OrderbookFeed.apply)
    val allCandlestickNames: List[FeedName.Candlesticks] = allCurrencyPairs.map(FeedName.Candlesticks.apply)

    allLevel2Names.prependedAll(allCandlestickNames) // plus others
  }

  def stream[M](feedDef: FeedName[M]): Stream[F, M]

  def name: ExchangeName
}

object ExchangeSpecific {
  // class Stub[F[_]: Async](
  //   allCurrencyPairs: List[(Currency, Currency)] = ???,
  //   activeCurrencyPairs: F[List[TradePair]] = ???,
  //   allFeedNames: List[FeedName[?]] = ???,
  //   stream[M](feedDef: FeedName[M]): Stream[F, M] = ???
  // ) extends ExchangeSpecific[F] {
  //   override
  // }

  //   override def allCurrencyPairs: List[(Currency, Currency)] = List(
  //     Currency("BTC") -> Currency("ETH"),
  //     Currency("BTC") -> Currency("USD")
  //   )

  //   override def activeCurrencyPairs: F[List[TradePair]] = allCurrencyPairs.tail.map(TradePair.apply).pure

  //   override def allFeedNames: List[FeedName[?]] = List(
  //     new FeedName.Stub,
  //     FeedName.Candlesticks(TradePair(Currency("BTC"), Currency("USD")))
  //   )

  //   override def stream[M](feedDef: FeedName[M]): Stream[F, M] = feedDef match {
  //     case OrderbookFeed(currency1, currency2) => ???
  //     case Candlesticks(_) =>
  //       Stream.fromIterator(
  //         iterator = Iterator.continually(
  //           Candlestick(1, 1, 1, 1)
  //         ),
  //         chunkSize = 1
  //       )
  //     case Stub(_value) => ???
  //   }
  // }
}

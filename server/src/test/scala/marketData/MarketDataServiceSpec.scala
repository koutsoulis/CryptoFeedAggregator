package marketData

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import fs2.Stream
import marketData.names.FeedName.OrderbookFeed
import marketData.exchange.Exchange
import marketData.names.TradePair
import marketData.names.Currency
import names.FeedName
import marketData.names.FeedName.Candlesticks
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import marketData.domain.Candlestick
import _root_.names.ExchangeName
import myMetrics.MyMetrics

object MarketDataServiceSpec extends SimpleIOSuite {
  val backingStreamsAndCallCount = Ref.of[IO, Int](0).map { ref =>
    val backingStreams: Exchange[IO] = new Exchange[IO] {

      override def name: ExchangeName = ExchangeName.Binance

      override def activeCurrencyPairs: IO[List[TradePair]] = allCurrencyPairs.pure

      override def allCurrencyPairs: List[TradePair] = List(
        Currency("BTC") -> Currency("ETH"),
        Currency("ETH") -> Currency("BTC")
      ).map(TradePair.apply)

      override def stream[Message](feed: FeedName[Message]): Stream[IO, Message] = feed match {
        case _: OrderbookFeed => ???
        case Candlesticks(tradePair) =>
          Stream.eval(ref.update(_ + 1)) >>
            Stream.fromIterator(Iterator.continually(Candlestick("0", 1, 1, 1, 1)), 1)
      }

    }

    backingStreams -> ref
  }

  test("reuses the backing stream when two requests overlap") {
    MyMetrics.stub[IO].use { case (_, _, incomingConcurrentStreamsGauge) =>
      for {
        (bStreams, callCount) <- backingStreamsAndCallCount
        marketDataService <- MarketDataService.apply(bStreams, ???)
        requestStubDatafeed = marketDataService.stream(bStreams.allFeedNames.head)
        _ <- requestStubDatafeed.zip(requestStubDatafeed).take(10).compile.toList.map(print)
        callCountV <- callCount.get
      } yield expect(callCountV == 1)
    }
  }

  test("preserves error thrown by Exchange service") {
    final case class DTOWhichRegressed(
        oldFieldName: String
    ) derives circe.Decoder

    val frameLoadWithNewIncompatibleStructure = """
      {"newFieldName":"whatever"}
    """

    val errorFromExchangeService = new Exception("error from Exchange service")

    MyMetrics.stub[IO].use { case (_, _, incomingConcurrentStreamsGauge) =>
      MarketDataService
        .apply(
          exchangeSpecific = new Exchange {

            override def name: ExchangeName = ExchangeName.Binance

            override def allCurrencyPairs: List[TradePair] = List(Currency("BTC") -> Currency("ETH")).map(TradePair.apply)

            override def activeCurrencyPairs: IO[List[TradePair]] = allCurrencyPairs.pure

            override def stream[M](feedDef: FeedName[M]): Stream[IO, M] = Stream.raiseError(errorFromExchangeService)
          },
          incomingConcurrentStreamsGauge = incomingConcurrentStreamsGauge
        ).flatMap(
          _.stream(FeedName.Candlesticks(TradePair(Currency("BTC"), Currency("ETH"))))
            .compile.drain.attempt
        ).map { outcome =>
          expect(outcome.swap.toOption.contains(errorFromExchangeService))
        }
    }
  }
}

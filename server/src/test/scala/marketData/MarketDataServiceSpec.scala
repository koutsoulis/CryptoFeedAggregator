package marketData

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.effect.*
import fs2.Stream
import marketData.domain.Candlestick
import marketData.exchange.Exchange
import marketData.names.Currency
import marketData.names.FeedName.Candlesticks
import marketData.names.FeedName.OrderbookFeed
import marketData.names.TradePair
import myMetrics.MyMetrics
import myMetrics.MyMetrics.IncomingConcurrentStreamsGauge
import weaver.IOSuite

import names.FeedName

object MarketDataServiceSpec extends IOSuite {

  override type Res = IncomingConcurrentStreamsGauge[IO]
  override def sharedResource: Resource[IO, IncomingConcurrentStreamsGauge[IO]] =
    MyMetrics.stub[IO].map(_._3).toResource

  val exchangeAndCallCount = Ref.of[IO, Int](0).map { ref =>
    val exchange: Exchange[IO] =
      Exchange.stub(
        streamStub = feedName =>
          feedName match {
            case _: OrderbookFeed => ???
            case Candlesticks(tradePair) =>
              Stream.eval(ref.update(_ + 1)) >>
                Stream.fromIterator(Iterator.continually(Candlestick("0", 1, 1, 1, 1).asInstanceOf[feedName.Message]), 1)
          }
      )

    exchange -> ref
  }

  test("reuses the backing stream when two requests overlap") { incomingConcurrentStreamsGauge =>
    for {
      (exchange, callCount) <- exchangeAndCallCount
      marketDataService <- MarketDataService.apply(exchange, incomingConcurrentStreamsGauge)
      arbitraryPair <- exchange.activeCurrencyPairs.map(_.head)
      requestStubDatafeed = marketDataService.stream(Candlesticks(arbitraryPair))
      _ <- requestStubDatafeed.zip(requestStubDatafeed).take(10).compile.drain
      callCountV <- callCount.get
    } yield expect(callCountV == 1)
  }

  test("concludes streaming of a FeedName and reliably streams anew") { incomingConcurrentStreamsGauge =>
    for {
      (exchange, callCount) <- exchangeAndCallCount
      marketDataService <- MarketDataService.apply(exchange, incomingConcurrentStreamsGauge)
      arbitraryPair <- exchange.activeCurrencyPairs.map(_.head)
      requestStubDatafeed = marketDataService.stream(Candlesticks(arbitraryPair))
      _ <- requestStubDatafeed.take(10).compile.drain
      outputSecondTime <- requestStubDatafeed.take(10).compile.toList
      callCountV <- callCount.get
    } yield expect(callCountV == 2) && expect(outputSecondTime.size == 10)
  }

  test("preserves error thrown by the Exchange service") { incomingConcurrentStreamsGauge =>
    val errorFromExchangeService = new Exception("error from Exchange service")

    MarketDataService
      .apply(
        exchange = Exchange.stub(streamStub = _ => Stream.raiseError(errorFromExchangeService)),
        incomingConcurrentStreamsGauge = incomingConcurrentStreamsGauge
      ).flatMap(
        _.stream(FeedName.Candlesticks(TradePair(Currency("BTC"), Currency("ETH"))))
          .compile.drain.attempt
      ).map { outcome =>
        expect(outcome.swap.toOption.contains(errorFromExchangeService))
      }
  }
}

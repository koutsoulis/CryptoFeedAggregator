package marketData

import weaver.IOSuite
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
import myMetrics.MyMetrics.IncomingConcurrentStreamsGauge

object MarketDataServiceSpec extends IOSuite {

  override type Res = IncomingConcurrentStreamsGauge[IO]
  override def sharedResource: Resource[IO, IncomingConcurrentStreamsGauge[IO]] =
    MyMetrics.stub[IO].map(_._3)

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

  test("preserves error thrown by Exchange service") { incomingConcurrentStreamsGauge =>
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

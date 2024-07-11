package marketData

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.syntax.all.*
import cats.effect.*
import fs2.{Pull, Stream}
import marketData.domain.Candlestick
import marketData.exchange.Exchange
import marketData.names.{Currency, FeedName, TradePair}
import marketData.names.FeedName.Candlesticks
import marketData.names.FeedName.OrderbookFeed
import myMetrics.MyMetrics
import myMetrics.MyMetrics.IncomingConcurrentStreamsGauge
import weaver.{Expect, IOSuite}
import fs2.timeseries.TimeStamped
import cats.effect.testkit.TestControl

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object MarketDataServiceSpec extends IOSuite {

  override type Res = IncomingConcurrentStreamsGauge[IO]
  override def sharedResource: Resource[IO, IncomingConcurrentStreamsGauge[IO]] =
    MyMetrics.stub[IO].map(_._3).toResource

  val exchangeAndCallCount: IO[(Exchange[IO], Ref[IO, Int])] = Ref.of[IO, Int](0).map { ref =>
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

  def exchangeStubMeteredStream(duration: FiniteDuration): Exchange[IO] = Exchange.stub(
    streamStub = feedName =>
      feedName match {
        case _: OrderbookFeed => ???
        case Candlesticks(tradePair) =>
          Stream
            .iterate(0)(_ + 1).map(int => Candlestick(int.toString, 1, 1, 1, 1).asInstanceOf[feedName.Message])
            .zipLeft(Stream.repeatEval(IO.sleep(duration))) // avoid .metered and friends for easier reasoning under TestControl
      }
  )

  val streamUnderTestFromDependencies: ((Exchange[IO], IncomingConcurrentStreamsGauge[IO])) => Resource[IO, Stream[IO, Candlestick]] =
    MarketDataService.apply[IO].tupled.andThen { marketDataServiceResource =>
      marketDataServiceResource.evalMap { marketDataService =>
        marketDataService
          .activeCurrencyPairs.map(_.head).map(Candlesticks.apply)
          .map(marketDataService.stream)
          .map(_.map(_.asInstanceOf[Candlestick]))

      }
    }

  test("#stream production rate is no higher than the backing stream's") { incomingConcurrentStreamsGauge =>
    TestControl.executeEmbed(
      streamUnderTestFromDependencies(exchangeStubMeteredStream(100.millis), incomingConcurrentStreamsGauge)
        .evalMap(_.take(3).evalMap(TimeStamped.monotonic).compile.toList)
        .map(_.map(_._1).sliding(2))
        .use { pairsOfTimestampAndItsSubsequentTimestamp =>
          expect(
            pairsOfTimestampAndItsSubsequentTimestamp.forall { pair => pair(1) == pair.head.plus(100.millis) }
          ).pure
        }
    )
  }

  test(
    "#stream drops stale elements of backing Exchange#stream when the consumer of #stream falls behind " +
      "i.e. the backing stream emitted another element before the consumer got a chance to pull the previous (now stale) one"
  ) { incomingConcurrentStreamsGauge =>
    val scenario = streamUnderTestFromDependencies(exchangeStubMeteredStream(100.millis), incomingConcurrentStreamsGauge).evalMap {
      streamUnderTest =>
        val scenarioAsPull = for {
          (firstProducedElement, restOfStream) <- streamUnderTest.pull.uncons1.map(_.get)
          _ <- Pull.eval(IO.sleep(201.millis)) // sleep long enough for the backing stream to produce the third candlestick (marked "2")
          (thirdProducedElement, _) <- restOfStream.pull.uncons1.map(_.get)
          _ <- Pull.output1((firstProducedElement, thirdProducedElement))
        } yield ()
        scenarioAsPull.stream.compile.onlyOrError
    }

    TestControl.executeEmbed(
      scenario.use { candlesticksFromConsumersPOV =>
        {
          expect(candlesticksFromConsumersPOV.head._1 == "0") && // expect first candlestick
          expect(candlesticksFromConsumersPOV(1)._1 == "2") // expect third candlestick
        }.pure
      }
    )
  }

  test("#stream reuses the backing Exchange#stream when two requests overlap") { incomingConcurrentStreamsGauge =>
    exchangeAndCallCount
      .toResource.flatMap { case (exchange, callCount) =>
        MarketDataService.apply(exchange, incomingConcurrentStreamsGauge).map(_ -> callCount)
      }.use { case (marketDataService, callCount) =>
        for {
          arbitraryPair <- marketDataService.activeCurrencyPairs.map(_.head)
          requestStubDatafeed = marketDataService.stream(Candlesticks(arbitraryPair))
          _ <- requestStubDatafeed.zip(requestStubDatafeed).take(10).compile.drain
          callCountV <- callCount.get
        } yield expect(callCountV == 1)
      }
  }

  test("#stream concludes streaming of a FeedName and reliably streams anew") { incomingConcurrentStreamsGauge =>
    exchangeAndCallCount
      .toResource.flatMap { case (exchange, callCount) =>
        MarketDataService.apply(exchange, incomingConcurrentStreamsGauge).map(_ -> callCount)
      }.use { case (marketDataService, callCount) =>
        for {
          arbitraryPair <- marketDataService.activeCurrencyPairs.map(_.head)
          requestStubDatafeed = marketDataService.stream(Candlesticks(arbitraryPair))
          _ <- requestStubDatafeed.take(10).compile.drain
          outputSecondTime <- requestStubDatafeed.take(10).compile.toList
          callCountV <- callCount.get
        } yield expect(callCountV == 2) && expect(outputSecondTime.size == 10)
      }
  }

  test("#stream preserves error thrown by backing Exchange#stream") { incomingConcurrentStreamsGauge =>
    val errorFromExchangeService = new Exception("error from Exchange service")

    MarketDataService
      .apply(
        exchange = Exchange.stub(streamStub = _ => Stream.raiseError(errorFromExchangeService)),
        incomingConcurrentStreamsGauge = incomingConcurrentStreamsGauge
      ).use(
        _.stream(FeedName.Candlesticks(TradePair(Currency("BTC"), Currency("ETH"))))
          .compile.drain.attempt
      ).map { outcome =>
        expect(outcome.swap.toOption.contains(errorFromExchangeService))
      }
  }
}

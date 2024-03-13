package marketData

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.service.BackingStreams
import fs2.Stream
import marketData.FeedDefinition.Level2
import marketData.FeedDefinition.Stub
import marketData.exchange.ExchangeParameters

object MarketDataServiceSpec extends SimpleIOSuite {
  val backingStreamsAndCallCount = Ref.of[IO, Int](0).map { ref =>
    // def emitStubMessagesForever[Message](stubFeed: FeedDefinition[Message]): Stream[IO, Message] =
    //   Stream.iterate(start = 0)(_ + 1).map(FeedDefinition.Stub.Message.apply).covary[IO]

    val backingStreams = new BackingStreams[IO] {

      override def stream[Message](feed: FeedDefinition[Message]): Stream[IO, Message] = feed match {
        case Level2(currency1, currency2) => ???
        case stubFeed: Stub =>
          Stream
            .eval(ref.update(_ + 1)) >> Stream
            .iterate(start = 0)(_ + 1).map(FeedDefinition.Stub.Message.apply)
            .covary[IO].evalTap(IO.print)
      }

    }

    backingStreams -> ref
  }

  test("reuses the backing stream when two requests overlap") {
    val exchangeParamsStub = ExchangeParameters.stub
    for {
      (bStreams, callCount) <- backingStreamsAndCallCount
      marketDataService <- MarketDataService.apply(bStreams, exchangeParamsStub)
      requestStubDatafeed = marketDataService.stream(exchangeParamsStub.allFeedDefs.head)
      _ <- requestStubDatafeed.zip(requestStubDatafeed).take(10).compile.toList.map(print)
      callCountV <- callCount.get
    } yield expect(callCountV == 1)
  }
}

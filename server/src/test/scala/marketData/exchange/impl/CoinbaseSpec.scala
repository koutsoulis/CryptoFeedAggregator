package marketData.exchange.impl

import cats.*
import cats.effect.*
import cats.syntax.all.*
import fs2.Stream
import marketData.domain.Orderbook
import marketData.exchange.impl.coinbase.dto.Level2Message
import marketData.exchange.impl.coinbase.dto.Level2Message.Relevant.Event
import marketData.exchange.impl.coinbase.dto.Level2Message.Relevant.Event.Update.Side
import marketData.names.Currency
import marketData.names.FeedName.OrderbookFeed
import marketData.names.TradePair
import mouse.all.*
import weaver.IOSuite

import scala.collection.immutable.TreeMap

// IDEA: ask chatgpt to generate test input as JSON; counterargument: breaks encapsulation as it relies on (the stubbed component's) coinbase.Client private logic to parse it
// counter-counter: it's ok to hinge on the parsing logic since we have high confidence in it
object CoinbaseSpec extends IOSuite {

  override type Res = List[marketData.domain.Orderbook]

  override def sharedResource: Resource[IO, Res] = {
    def updateEvent(updates: List[Event.Update]): Event =
      Level2Message
        .Relevant.Event(
          `type` = Event.Type.update,
          updates = updates
        )

    val firstUpdateEvent = List[(Side, BigDecimal, BigDecimal)](
      (Side.offer, 8, 9),
      (Side.bid, 1, 3),
      (Side.bid, 4, 6),
      (Side.offer, 5, 7)
    ).map(Level2Message.Relevant.Event.Update.apply)
      .thrush(updateEvent)

    val secondUpdateEvent = List[(Side, BigDecimal, BigDecimal)](
      (Side.offer, 5, 6),
      (Side.offer, 1, 0)
    ).map(Level2Message.Relevant.Event.Update.apply)
      .thrush(updateEvent)

    val l2snapshotEvent = {
      val snapshotUpdatesBidSide = List[(BigDecimal, BigDecimal)](
        (1, 2),
        (3, 4)
      ).map(Level2Message.Relevant.Event.Update(Side.bid, _, _))

      val snapshotUpdatesOfferSide = List[(BigDecimal, BigDecimal)](
        (1, 2),
        (5, 6)
      ).map(Level2Message.Relevant.Event.Update(Side.offer, _, _))

      Level2Message
        .Relevant.Event(
          `type` = Event.Type.snapshot,
          updates = snapshotUpdatesBidSide ++ snapshotUpdatesOfferSide
        )
    }

    val firstMessage = Level2Message.Relevant(
      events = List(
        l2snapshotEvent,
        firstUpdateEvent
      )
    )

    val secondMessage = Level2Message.Relevant(
      events = List(
        secondUpdateEvent
      )
    )

    val snapshot = Level2Message.Relevant

    val serviceUnderTest: Coinbase[IO] = {
      val coinbaseClientStub = coinbase
        .Client.stub(
          level2MessagesStub = _ => Stream.emits(List(firstMessage, secondMessage))
        )

      new Coinbase[IO](
        client = coinbaseClientStub,
        allCurrencyPairs = List.empty
      )
    }

    serviceUnderTest
      .stream(OrderbookFeed(TradePair(Currency("BTC"), Currency("ETH"))))
      .compile.toList.toResource
  }

  test("orderbook from initial snapshot") { orderbooksTestOutput =>
    expect {
      orderbooksTestOutput(0) ==
        Orderbook(
          bidLevelToQuantity = TreeMap(1 -> 2, 3 -> 4).map(BigDecimal(_) -> BigDecimal(_)),
          askLevelToQuantity = TreeMap(1 -> 2, 5 -> 6).map(BigDecimal(_) -> BigDecimal(_))
        )
    }.pure
  }

  test("orderbook after the first update event is consumed") { orderbooksTestOutput =>
    expect {
      orderbooksTestOutput(1) ==
        Orderbook(
          bidLevelToQuantity = TreeMap(1 -> 3, 3 -> 4, 4 -> 6).map(BigDecimal(_) -> BigDecimal(_)),
          askLevelToQuantity = TreeMap(1 -> 2, 5 -> 7, 8 -> 9).map(BigDecimal(_) -> BigDecimal(_))
        )
    }.pure
  }

  test("orderbook after the second update event is consumed") { orderbooksTestOutput =>
    expect {
      orderbooksTestOutput(2) ==
        Orderbook(
          bidLevelToQuantity = TreeMap(1 -> 3, 3 -> 4, 4 -> 6).map(BigDecimal(_) -> BigDecimal(_)),
          askLevelToQuantity = TreeMap(5 -> 6, 8 -> 9).map(BigDecimal(_) -> BigDecimal(_))
        )
    }.pure
  }

  test("number of orderbooks emitted equal to number of updates consumed plus the snapshot") { orderbooksTestOutput =>
    expect(orderbooksTestOutput.size == 3).pure
  }

  // TODO
  // test("Coinbase#stream on Candlesticks") { ??? }
}

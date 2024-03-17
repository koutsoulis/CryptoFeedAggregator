package marketData.exchange.impl

import marketData.exchange.ExchangeSpecific
import marketData.Currency
import marketData.FeedDefinition
import org.http4s.client.websocket.WSRequest
import marketData.FeedDefinition.OrderbookFeed
import marketData.FeedDefinition.Stub
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.Uri
import org.http4s.Request
import org.http4s.dsl.*
import org.http4s.implicits.*
import cats.MonadThrow
import upperbound.*
import upperbound.syntax.rate.*
import scala.concurrent.duration.*
import binance.{dto, domain}
import marketData.exchange.impl.binance.domain.RateLimits
import cats.effect.std.Semaphore
import fs2.{Stream, Pull}
import fs2.concurrent.Signal
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import _root_.io.circe

class Binance private extends ExchangeSpecific {

  override def allCurrencyPairs: List[(Currency, Currency)] = ???
}

object Binance {
  val baseEndpoint = uri"https://api.binance.com"
  val exchangeInfoRequestWeight = 20
  def symbol(currency: Currency): String = ???
  def tradePairSymbol(currency1: Currency, currency2: Currency): String =
    symbol(currency1) ++ symbol(currency2)

  def apply[F[_]](
      client: http4s.client.Client[F],
      wsClient: http4s.client.websocket.WSClientHighLevel[F]
  )(
      using F: Async[F]
  ): F[Binance] = {

    // def level2Stream[F[_]](level2Def: FeedDefinition.Level2)(
    //     using F: MonadThrow[F]
    // ): fs2.Stream[F, FeedDefinition.Level2.Message] = {
    // }

    def websocketRequest(feedDefinition: FeedDefinition[?]): F[WSRequest] = {
      val uriOrParseFailure = feedDefinition match {
        case OrderbookFeed(currency1, currency2) =>
          Uri.fromString(s"wss://stream.binance.com:9443/ws/${tradePairSymbol(currency1, currency2)}@depth@100ms")

        case Stub(_value) => ???
      }

      F.pure(uriOrParseFailure).rethrow.map(WSRequest.apply)
    }

    // Assumption: ws market stream messages are guaranteed to arrive in order
    def level2UpdateStream(level2Def: FeedDefinition.OrderbookFeed): fs2.Stream[F, domain.Orderbook] = level2Def match {
      case OrderbookFeed(currency1, currency2) =>
        val establishStream = F
          .fromEither(
            Uri.fromString(s"wss://stream.binance.com:9443/ws/${tradePairSymbol(currency1, currency2)}@depth@100ms")
          )
          .map(WSRequest.apply)
          .map(wsClient.connectHighLevel)
          .map(Stream.resource)
          .map(_.flatMap(_.receiveStream))

        /**
         * We assume there are no messages missing or out of order
         */
        val orderbookUpdates = Stream
          .eval(establishStream).flatten
          .evalMapChunk {
            case Text(data, _) => F.delay(circe.parser.decode[dto.OrderbookUpdate](data)).rethrow
            case _: Binary => F.raiseError(new Exception("Expected text but received binary ws frame on Binance level2 update stream"))
          }
          .map(domain.OrderbookUpdate.transformer.transform)

        val orderbookSnapshotsAsNestedPull = for {
          firstUpdateMaybe <- orderbookUpdates.pull.uncons1
          (firstUpdate, rest) <- Pull.eval(F.fromOption(firstUpdateMaybe, new Exception))
          snapshotIssuedAfterFirstUpdate <- Pull.eval(level2InitialSnapshot(level2Def))
          relevantUpdates = rest.dropWhile(_.lastUpdateId <= snapshotIssuedAfterFirstUpdate.lastUpdateId)
          orderbookSnapshots = relevantUpdates
            .scan(snapshotIssuedAfterFirstUpdate) { case (snapshot, update) =>
              snapshot.updateWith(update)
            }.pull.echo
        } yield orderbookSnapshots

        orderbookSnapshotsAsNestedPull.flatten.stream
    }

    def level2InitialSnapshot(level2Def: FeedDefinition.OrderbookFeed): F[domain.Orderbook] = {
      val uriOrParseFailure = level2Def match {
        case OrderbookFeed(currency1, currency2) =>
          Uri.fromString(s"https://api.binance.com/api/v3/depth?symbol=${tradePairSymbol(currency1, currency2)}&limit=1000")
      }

      F.fromEither(uriOrParseFailure)
        .flatMap(client.expect[dto.Orderbook].apply)
        .map(domain.Orderbook.transformer.transform)
    }

    client
      .expect[dto.ExchangeInfo](baseEndpoint.addPath("/api/v3/exchangeInfo"))
      .map(domain.RateLimits.of).rethrow.flatMap { case RateLimits(requestWeight, rawRequests) =>
        Semaphore(requestWeight.permitsAvailable - exchangeInfoRequestWeight)
      }
    ???
  }
}

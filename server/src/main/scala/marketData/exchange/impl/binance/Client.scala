package marketData.exchange.impl.binance

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.Currency
import marketData.exchange.impl.binance.domain.Orderbook
import cats.effect.*
import org.http4s.client.websocket
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import scala.concurrent.duration.Duration
import cats.effect.std.Semaphore
import org.http4s.Uri
import marketData.exchange.impl.Binance
import Client.*
import _root_.io.circe
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Canceled
import org.http4s.client.websocket.WSRequest
import fs2.Stream
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary

class Client[F[_]](
    httpClient: http4s.client.Client[F],
    wsClient: websocket.WSClientHighLevel[F],
    requestWeight: RLSemaphoreAndReleaseTime[F],
    rawRequests: RLSemaphoreAndReleaseTime[F],
    wsEstablishConnectionRL: RLSemaphoreAndReleaseTime[F]
)(using F: Async[F]) {
  def orderbookSnapshot(currency1: Currency, currency2: Currency): F[Orderbook] =
    httpRequest[dto.Orderbook](
      s"https://api.binance.com/api/v3/depth?symbol=${Binance.tradePairSymbol(currency1, currency2)}&limit=1000",
      requestWeight,
      50
    )
      .map(domain.Orderbook.transformer.transform)

  /**
   * start count-down to release permits after response is back, in case request emission intervals are shorter on the receiving side due to
   * i.e. network conditions
   *
   * TODO reattempt & backoff
   *
   * @param uri
   * @param relevantSemaphoreAndRTime
   * @param permitsNeeded
   * @return
   */
  private def httpRequest[Out: circe.Decoder](
      uri: String,
      relevantSemaphoreAndRTime: RLSemaphoreAndReleaseTime[F],
      permitsNeeded: Int
  ): F[Out] =
    F.bracketFull { poll =>
      F.fromEither(Uri.fromString(uri)) <*
        poll(relevantSemaphoreAndRTime.semaphore.acquireN(permitsNeeded))
    }(
      httpClient.expect[Out].apply
    ) { (_, _) =>
      F.start(F.sleep(relevantSemaphoreAndRTime.releaseTime) *> relevantSemaphoreAndRTime.semaphore.releaseN(permitsNeeded)).void
    }

  /**
   * Assumption: ws market stream messages are guaranteed to arrive in order
   *
   * Starts counting down to release the permits after the websocket connection has been established. It would be more pragmatic (easier to
   * express, reason about and correct enough) if the countdown started right before emitting the request to establish the connection , by
   * also incrementing the time to countdown by say ~2secs to account for possible high latencies. And if the 2 secs are not enough and we
   * hit the rate limits not bother recover.
   *
   * @param uri
   * @return
   */
  private def wsConnect[Out: circe.Decoder](uri: String): Stream[F, Out] = {
    val establishWSConnection = F.bracketFull { poll =>
      F.fromEither(Uri.fromString(uri)).map(WSRequest.apply) <*
        poll(wsEstablishConnectionRL.semaphore.acquire)
    } { wsRequest =>
      wsClient.connectHighLevel(wsRequest).allocatedCase
    } { (_, _) =>
      F.start(F.sleep(wsEstablishConnectionRL.releaseTime) *> wsEstablishConnectionRL.semaphore.release).void
    }

    Stream
      .resource(
        Resource.applyFull[F, websocket.WSConnectionHighLevel[F]] { poll => poll(establishWSConnection) }
      ).flatMap(_.receiveStream).evalMapChunk {
        case Text(data, _) => F.fromEither(circe.parser.decode[Out](data))
        case _: Binary => F.raiseError(new Exception(s"Expected text but received binary ws frame while consuming stream of $uri"))
      }
  }

}

object Client {
  case class RLSemaphoreAndReleaseTime[F[_]](semaphore: Semaphore[F], releaseTime: Duration)
}

// object Client{
//   case class RateLimitPermitsAndReleaseTimes
// }

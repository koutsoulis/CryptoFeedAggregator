package client

import _root_.io.circe
import cats.*
import cats.effect.*
import cats.syntax.all.*
import client.rateLimits.RLSemaphoreAndReleaseTime
import org.http4s
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec.*

trait RateLimitedHttpClient[F[_]: Async] {
  def get[Out: circe.Decoder](
      uri: Uri,
      permitsNeeded: Int
  ): F[Out]
}

object RateLimitedHttpClient {
  class RateLimitedHttpClientLive[F[_]](
      httpClient: http4s.client.Client[F],
      rateLimitsData: RLSemaphoreAndReleaseTime[F]
  )(
      using F: Async[F]
  ) extends RateLimitedHttpClient {

    /**
     * start count-down to release permits after response is back, just in case intervals between requests are shorter on the receiving side
     * due to i.e. network conditions
     *
     * TODO reattempt & backoff
     */
    override def get[Out: circe.Decoder](
        uri: Uri,
        permitsNeeded: Int
    ): F[Out] =
      F.bracketFull { poll =>
        poll(rateLimitsData.semaphore.acquireN(permitsNeeded))
      }(_ => httpClient.expect[Out](uri)) { (_, _) =>
        F.start(F.sleep(rateLimitsData.releaseTime) *> rateLimitsData.semaphore.releaseN(permitsNeeded)).void
      }
  }
}

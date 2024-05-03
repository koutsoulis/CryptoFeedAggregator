package client

import client.rateLimits.RLSemaphoreAndReleaseTime
import _root_.io.circe
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.Uri

trait RateLimitedHttpClient[F[_]: Async] {
  def get[Out: circe.Decoder](
      uri: String,
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
        uri: String,
        permitsNeeded: Int
    ): F[Out] =
      F.bracketFull { poll =>
        F.fromEither(Uri.fromString(uri)) <*
          poll(rateLimitsData.semaphore.acquireN(permitsNeeded))
      }(
        httpClient.expect[Out].apply
      ) { (_, _) =>
        F.start(F.sleep(rateLimitsData.releaseTime) *> rateLimitsData.semaphore.releaseN(permitsNeeded)).void
      }
  }
}

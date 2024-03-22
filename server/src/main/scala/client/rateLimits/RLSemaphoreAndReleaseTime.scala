package client.rateLimits

import cats.effect.*
import cats.effect.std.Semaphore
import scala.concurrent.duration.Duration

/**
 * Holds what needed to ensure we don't hit rate limits
 *
 * @param semaphore
 *   Holds the permits available
 * @param releaseTime
 *   After acquiring a number of permits at once, this is how long you have to wait before releasing them
 */
final case class RLSemaphoreAndReleaseTime[F[_]](semaphore: Semaphore[F], releaseTime: Duration)

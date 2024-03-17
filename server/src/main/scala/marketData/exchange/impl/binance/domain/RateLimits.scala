package marketData.exchange.impl.binance.domain

import scala.concurrent.duration.Duration
import concurrent.duration.DurationInt
import marketData.exchange.impl.binance.dto
import cats.MonadThrow
import marketData.exchange.impl.binance.dto.ExchangeInfo.RateLimit.Interval.{SECOND, MINUTE, HOUR, DAY}
import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.data.Validated.Valid
import marketData.exchange.impl.binance.dto.ExchangeInfo
import marketData.exchange.impl.binance.dto.ExchangeInfo.RateLimit.RLType

final case class RateLimits(
    requestWeight: RateLimits.RateLimit,
    rawRequests: RateLimits.RateLimit
)

object RateLimits {
  trait RateLimit {
    def timeToReleasePermits: Duration
    def permitsAvailable: Int
  }

  def of(exchangeInfo: dto.ExchangeInfo): Either[Exception, RateLimits] = {
    val rlTypeToRateLimit: Map[RLType, ExchangeInfo.RateLimit] = exchangeInfo
      .rateLimits.map { rl =>
        rl.rateLimitType -> rl
      }.toMap

    def getSpecificRateLimitType(rlType: dto.ExchangeInfo.RateLimit.RLType): Either[Exception, RateLimit] =
      rlTypeToRateLimit
        .get(rlType).map { case dto.ExchangeInfo.RateLimit(_, interval, intervalNum, limit) =>
          new RateLimit {
            override val timeToReleasePermits = interval match {
              case SECOND => intervalNum.seconds
              case MINUTE => intervalNum.minutes
              case HOUR => intervalNum.hours
              case DAY => intervalNum.days
            }

            override val permitsAvailable = limit
          }
        }.toRight(new Exception(s"ExchangeInfo did not contain entry for Rate Limit type: $rlType"))

    (
      getSpecificRateLimitType(dto.ExchangeInfo.RateLimit.RLType.REQUEST_WEIGHT),
      getSpecificRateLimitType(dto.ExchangeInfo.RateLimit.RLType.RAW_REQUESTS)
    ).mapN(RateLimits.apply)
  }
}

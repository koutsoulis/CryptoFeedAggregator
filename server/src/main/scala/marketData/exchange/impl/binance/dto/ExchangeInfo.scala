package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

final case class ExchangeInfo(
    rateLimits: List[ExchangeInfo.RateLimit]
) derives circe.Decoder

object ExchangeInfo {
  final case class RateLimit(
      rateLimitType: RateLimit.RLType,
      interval: RateLimit.Interval,
      intervalNum: Int,
      limit: Int
  ) derives circe.Decoder

  object RateLimit {
    sealed trait RLType
    object RLType {
      object REQUEST_WEIGHT extends RLType
      object RAW_REQUESTS extends RLType
      object Ignore extends RLType

      implicit val rlTypeDecoder: circe.Decoder[RLType] = circe.Decoder[String].map {
        case "REQUEST_WEIGHT" => REQUEST_WEIGHT
        case "RAW_REQUESTS" => RAW_REQUESTS
        case _ => Ignore
      }
    }

    sealed trait Interval
    object Interval {
      object SECOND extends Interval
      object MINUTE extends Interval
      object HOUR extends Interval
      object DAY extends Interval

      implicit val intervalDecoder: circe.Decoder[Interval] = circe.Decoder[String].emap {
        case "SECOND" => Right(SECOND)
        case "MINUTE" => Right(MINUTE)
        case "HOUR" => Right(HOUR)
        case "DAY" => Right(DAY)
        case string => Left(s"unknown binance ratelimit interval type: $string")
      }
    }
  }
}

package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import _root_.io.scalaland.chimney
import marketData.names.Currency
import marketData.names.TradePair

import scala.util.Try

// https://binance-docs.github.io/apidocs/spot/en/#exchange-information

final case class ExchangeInfo(
    rateLimits: List[ExchangeInfo.RateLimit],
    symbols: List[ExchangeInfo.SymbolPair]
) derives circe.Decoder

object ExchangeInfo {
  final case class RateLimit(
      rateLimitType: RateLimit.RLType,
      interval: RateLimit.Interval,
      intervalNum: Int,
      limit: Int
  ) derives circe.Decoder

  object RateLimit {
    enum RLType {
      case REQUEST_WEIGHT, RAW_REQUESTS, Ignore
    }

    object RLType {
      given circe.Decoder[RLType] = circe.Decoder[String].emapTry { s => Try(RLType.valueOf(s)) }.or(circe.Decoder.const(Ignore))
    }

    enum Interval {
      case SECOND, MINUTE, HOUR, DAY
    }
    object Interval {
      given circe.Decoder[Interval] = circe.Decoder[String].emapTry { s => Try(Interval.valueOf(s)) }
    }

  }

  final case class SymbolPair(
      status: SymbolPair.Status,
      baseAsset: String,
      quoteAsset: String
  ) derives circe.Decoder {
    def baseAssetCurrency: Currency = Currency(baseAsset)
    def quoteAssetCurrency: Currency = Currency(quoteAsset)
  }

  object SymbolPair {
    enum Status {
      case TRADING, Ignore
    }

    given circe.Decoder[Status] = circe.Decoder[String].map {
      case "TRADING" => Status.TRADING
      case _ => Status.Ignore
    }

    given chimney.Transformer[SymbolPair, TradePair] = chimney
      .Transformer.define[SymbolPair, TradePair]
      .withFieldRenamed(_.baseAsset, _.base)
      .withFieldRenamed(_.quoteAsset, _.quote)
      .buildTransformer
  }
}

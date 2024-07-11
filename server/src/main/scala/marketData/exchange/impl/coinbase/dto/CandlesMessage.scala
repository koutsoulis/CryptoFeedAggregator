package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.scalaland.chimney
import cats.*
import cats.syntax.all.*
import marketData.domain.Candlestick

// https://docs.cloud.coinbase.com/advanced-trade/docs/ws-channels#candles-channel

sealed trait CandlesMessage

object CandlesMessage {
  case class Ignore(channel: ChannelToIgnore) extends CandlesMessage derives circe.Decoder

  case class Relevant(
      events: List[Relevant.Event]
  ) extends CandlesMessage
      derives circe.Decoder

  object Relevant {
    case class Event(
        candles: List[Event.Candle]
    ) derives circe.Decoder

    object Event {
      case class Candle(
          start: String, // in seconds
          high: BigDecimal,
          low: BigDecimal,
          open: BigDecimal,
          close: BigDecimal
      ) derives circe.Decoder

      object Candle {
        given chimney.Transformer[Candle, Candlestick] = chimney
          .Transformer.define[Candle, Candlestick]
          .withFieldComputed(_.startTimeInMsSinceUnixEpoch, _.start.toLong.*(1000).toString)
          .buildTransformer
      }
    }
  }

  given circe.Decoder[CandlesMessage] =
    circe.Decoder[Ignore].or(circe.Decoder[Relevant].widen)
}

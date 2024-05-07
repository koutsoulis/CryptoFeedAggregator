package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Candlestick
import scala.util.Try

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
          start: String,
          high: BigDecimal,
          low: BigDecimal,
          open: BigDecimal,
          close: BigDecimal
      ) derives circe.Decoder

      object Candle {
        given chimney.Transformer[Candle, Candlestick] = chimney
          .Transformer.define[Candle, Candlestick]
          .withFieldRenamed(_.start, _.startTimeInMsSinceUnixEpoch)
          .buildTransformer
      }
    }
  }

  given circe.Decoder[CandlesMessage] =
    circe.Decoder[Ignore].or(circe.Decoder[Relevant].widen)
}

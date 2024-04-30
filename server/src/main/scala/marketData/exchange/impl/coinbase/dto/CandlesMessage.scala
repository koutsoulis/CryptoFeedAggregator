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

sealed trait CandlesMessage

object CandlesMessage {
  case class Ignore(channel: "subscriptions") extends CandlesMessage derives circe.Decoder

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
          start: Long,
          high: BigDecimal,
          low: BigDecimal,
          open: BigDecimal,
          close: BigDecimal
      ) derives circe.Decoder

      object Candle {
        given chimney.Transformer[Candle, Candlestick] = chimney.Transformer.define[Candle, Candlestick].buildTransformer
      }
    }
  }

  given circe.Decoder[CandlesMessage] =
    circe.Decoder[Ignore].or(circe.Decoder[Relevant].widen)
}

// // Candles Message
// {
//   "channel": "candles",
//   "client_id": "",
//   "timestamp": "2023-06-09T20:19:35.39625135Z",
//   "sequence_num": 0,
//   "events": [
//     {
//       "type": "snapshot",
//       "candles": [
//         {
//           "start": "1688998200",
//           "high": "1867.72",
//           "low": "1865.63",
//           "open": "1867.38",
//           "close": "1866.81",
//           "volume": "0.20269406",
//           "product_id": "ETH-USD",
//         }
//       ]
//     }
//   ]
// }

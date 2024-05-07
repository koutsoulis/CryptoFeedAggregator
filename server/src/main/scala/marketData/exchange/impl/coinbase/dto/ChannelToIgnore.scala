package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import scala.util.Try

// https://docs.cloud.coinbase.com/advanced-trade/docs/ws-channels

enum ChannelToIgnore {
  case subscriptions, heartbeats
}

object ChannelToIgnore {
  given circe.Decoder[ChannelToIgnore] = circe.Decoder[String].emapTry { string => Try(ChannelToIgnore.valueOf(string)) }
}

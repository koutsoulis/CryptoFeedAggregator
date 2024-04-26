package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import marketData.names.TradePair
import marketData.names.FeedName

case class SubscribeRequest private (
    `type`: String,
    product_ids: String,
    channels: List[String],
    signature: String,
    key: String,
    passphrase: String,
    timestamp: String
) derives circe.Encoder.AsObject

object SubscribeRequest {
  def apply(
      feedName: FeedName[?]
  ): SubscribeRequest = ???
}
// {
//     "type": "subscribe",
//     "product_ids": [
//         "BTC-USD"
//     ],
//     "channels": [
//         "full"
//     ],
//     "signature": "...",
//     "key": "...",
//     "passphrase": "...",
//     "timestamp": "..."
// }

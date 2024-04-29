package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import marketData.names.TradePair
import marketData.names.FeedName
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Candlesticks
import marketData.exchange.impl.coinbase.constants

case class SubscribeRequest private (
    `type`: String,
    product_ids: List[String],
    channel: SubscribeRequest.Channel
) derives circe.Encoder.AsObject

object SubscribeRequest {
  type Channel = "level2" | Nothing

  def apply(
      feedName: FeedName[?],
      channel: Channel
  ): SubscribeRequest = feedName match {
    case OrderbookFeed(tradePair) =>
      SubscribeRequest(
        `type` = "subscribe",
        product_ids = List(constants.product_ids(tradePair)),
        channel = channel
      )
    case Candlesticks(tradePair) => ???
  }
}

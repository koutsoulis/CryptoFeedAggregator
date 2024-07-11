package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import marketData.names.FeedName
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Candlesticks
import marketData.exchange.impl.coinbase.constants
import monocle.syntax.all.*
import marketData.names.FeedName.FeedNameQ

// https://docs.cloud.coinbase.com/advanced-trade/docs/ws-overview#sending-messages-without-api-keys

case class SubscribeRequest private (
    `type`: String,
    product_ids: List[String],
    channel: SubscribeRequest.Channel
) derives circe.Encoder

object SubscribeRequest {
  enum Channel {
    case level2, candles, heartbeats
  }

  object Channel {
    given circe.Encoder[Channel] = circe.Encoder[String].contramap[Channel](_.toString)
  }

  def relevantAndHeartbeats(
      feedName: FeedNameQ
  ): List[SubscribeRequest] = {
    val relevantSubscription = feedName match {
      case OrderbookFeed(tradePair) =>
        SubscribeRequest(
          `type` = "subscribe",
          product_ids = List(constants.product_ids(tradePair)),
          channel = Channel.level2
        )
      case Candlesticks(tradePair) =>
        SubscribeRequest(
          `type` = "subscribe",
          product_ids = List(constants.product_ids(tradePair)),
          channel = Channel.candles
        )
    }

    List(
      relevantSubscription,
      relevantSubscription.focus(_.channel).replace(Channel.heartbeats)
    )
  }
}

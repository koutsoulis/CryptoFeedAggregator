package marketData.exchange.impl.coinbase

import scala.concurrent.duration.DurationInt
import marketData.names.TradePair

object constants {
  // 26 April 2024
  // https://docs.cloud.coinbase.com/exchange/docs/websocket-rate-limits
  val websocketRequestsPerSecondPerIP = 8
  val websocketRateLimitRefreshPeriod = 1.second
  def product_ids(tradePair: TradePair): String = tradePair.base.name ++ "-" ++ tradePair.quote.name
}

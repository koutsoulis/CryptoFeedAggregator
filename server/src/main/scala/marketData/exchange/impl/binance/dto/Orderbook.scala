package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

final case class Orderbook(
    lastUpdateId: Long,
    bids: List[(BigDecimal, BigDecimal)],
    asks: List[(BigDecimal, BigDecimal)]
) derives circe.Decoder

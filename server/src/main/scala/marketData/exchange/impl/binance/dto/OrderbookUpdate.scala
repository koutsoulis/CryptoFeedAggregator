package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

final case class OrderbookUpdate(
    U: Long, // First update ID in event
    u: Long, // Final update ID in event
    b: List[(BigDecimal, BigDecimal)], // bids list of Price level to be updated and Quantity
    a: List[(BigDecimal, BigDecimal)] // asks list of Price level to be updated and Quantity
) derives circe.Decoder

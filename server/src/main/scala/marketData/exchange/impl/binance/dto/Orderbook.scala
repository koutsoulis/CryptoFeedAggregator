package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import _root_.io.scalaland.chimney
import marketData.exchange.impl.binance.domain

// https://binance-docs.github.io/apidocs/spot/en/#order-book

final case class Orderbook(
    lastUpdateId: Long,
    bids: List[(BigDecimal, BigDecimal)],
    asks: List[(BigDecimal, BigDecimal)]
) derives circe.Decoder

object Orderbook {
  val transformer: chimney.Transformer[Orderbook, domain.Orderbook] = chimney
    .Transformer.define[Orderbook, domain.Orderbook]
    .withFieldRenamed(_.asks, _.askLevelToQuantity)
    .withFieldRenamed(_.bids, _.bidLevelToQuantity)
    .buildTransformer
}

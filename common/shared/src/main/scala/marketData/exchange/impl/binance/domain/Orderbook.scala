package marketData.exchange.impl.binance.domain

import scala.collection.immutable.TreeMap
import cats.*
import cats.data.*
import cats.syntax.all.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*

final case class Orderbook(
    lastUpdateId: Long,
    bidLevelToQuantity: TreeMap[BigDecimal, BigDecimal],
    askLevelToQuantity: TreeMap[BigDecimal, BigDecimal]
)

object Orderbook {
  given chimney.Transformer[Orderbook, marketData.domain.Orderbook] =
    chimney.Transformer.derive

}

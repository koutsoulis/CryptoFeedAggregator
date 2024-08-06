package marketData.domain

import scala.collection.immutable.TreeMap
import cats.*
import cats.data.*
import cats.syntax.all.*
import io.bullet.borer
import io.bullet.borer.derivation.ArrayBasedCodecs.*
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import servingRoutes.dto.ServerJSON.ToServerJSON
import servingRoutes.dto.ServerJSON

case class Orderbook(
    bidLevelToQuantity: TreeMap[BigDecimal, BigDecimal],
    askLevelToQuantity: TreeMap[BigDecimal, BigDecimal]
) derives borer.Codec

object Orderbook {
  given ToServerJSON[Orderbook] = new ToServerJSON[Orderbook] {
    override def serverJSON(a: Orderbook) = a.transformInto[ServerJSON.Orderbook]
  }
}

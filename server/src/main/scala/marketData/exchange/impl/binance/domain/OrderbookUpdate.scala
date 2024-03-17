package marketData.exchange.impl.binance.domain

import marketData.exchange.impl.binance.dto
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import cats.*
import cats.data.*
import cats.syntax.all.*

final case class OrderbookUpdate(
    firstUpdateId: Long,
    lastUpdateId: Long,
    bidLevelToQuantity: Map[BigDecimal, BigDecimal],
    askLevelToQuantity: Map[BigDecimal, BigDecimal]
) {

  /**
   * Optimizes for this.{bidLevelToQuantity, askLevelToQuantity} larger than their counterparts in that. that is used to update this so the
   * keys of that take precedence
   */
  def add(that: OrderbookUpdate): OrderbookUpdate = OrderbookUpdate(
    firstUpdateId = math.min(this.firstUpdateId, that.firstUpdateId),
    lastUpdateId = math.max(this.lastUpdateId, that.lastUpdateId),
    bidLevelToQuantity = that.bidLevelToQuantity.foldLeft(this.bidLevelToQuantity) { case (acc, (level, quantity)) =>
      acc.updated(level, quantity)
    },
    askLevelToQuantity = that.askLevelToQuantity.foldLeft(this.askLevelToQuantity) { case (acc, (level, quantity)) =>
      acc.updated(level, quantity)
    }
  )
}

object OrderbookUpdate {
  val transformer: chimney.Transformer[dto.OrderbookUpdate, OrderbookUpdate] = chimney
    .Transformer.define[dto.OrderbookUpdate, OrderbookUpdate]
    .withFieldRenamed(_.U, _.firstUpdateId)
    .withFieldRenamed(_.u, _.lastUpdateId)
    .withFieldRenamed(_.a, _.askLevelToQuantity)
    .withFieldRenamed(_.b, _.bidLevelToQuantity)
    .buildTransformer
}

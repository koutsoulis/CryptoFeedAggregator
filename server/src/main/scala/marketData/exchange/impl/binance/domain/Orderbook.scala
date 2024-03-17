package marketData.exchange.impl.binance.domain

import marketData.exchange.impl.binance.dto

import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import io.scalaland.chimney.inlined.*

final case class Orderbook(
    lastUpdateId: Long,
    bidLevelToQuantity: Map[BigDecimal, BigDecimal],
    askLevelToQuantity: Map[BigDecimal, BigDecimal]
) {

  /**
   * @param update
   *   Guaranteed to be at least as up-to-date as `this` (reflected in their respective #lastUpdateId)
   */
  def updateWith(update: OrderbookUpdate): Orderbook =
    Orderbook(
      lastUpdateId = update.lastUpdateId,
      bidLevelToQuantity = update.bidLevelToQuantity.foldLeft(this.bidLevelToQuantity) { case (acc, (level, quantity)) =>
        acc.updatedWith(level) { _ => Some(quantity).filter(_ > 0) }
      },
      askLevelToQuantity = update.askLevelToQuantity.foldLeft(this.askLevelToQuantity) { case (acc, (level, quantity)) =>
        acc.updatedWith(level) { _ => Some(quantity).filter(_ > 0) }
      }
    )
}

object Orderbook {
  val transformer: chimney.Transformer[dto.Orderbook, Orderbook] = chimney
    .Transformer.define[dto.Orderbook, Orderbook]
    .withFieldRenamed(_.asks, _.askLevelToQuantity)
    .withFieldRenamed(_.bids, _.bidLevelToQuantity)
    .buildTransformer
}

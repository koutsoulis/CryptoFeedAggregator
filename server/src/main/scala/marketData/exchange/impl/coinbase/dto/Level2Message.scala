package marketData.exchange.impl.coinbase.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Orderbook
import marketData.exchange.impl.coinbase.dto.Level2Message.L2Update.Side
import monocle.syntax.all.*

sealed trait Level2Message

object Level2Message {
  case class Snapshot(
      bids: List[(BigDecimal, BigDecimal)],
      asks: List[(BigDecimal, BigDecimal)]
  ) extends Level2Message
      derives circe.Decoder

  object Snapshot {
    given chimney.Transformer[Snapshot, Orderbook] = chimney
      .Transformer.define[Snapshot, Orderbook]
      .withFieldRenamed(_.asks, _.askLevelToQuantity)
      .withFieldRenamed(_.bids, _.bidLevelToQuantity)
      .buildTransformer
  }

  case class L2Update(
      changes: List[(L2Update.Side, BigDecimal, BigDecimal)]
  ) extends Level2Message
      derives circe.Decoder {
    def apply(orderbook: Orderbook): Orderbook =
      changes.foldLeft(orderbook) { case (orderbook, (side, price, size)) =>
        side match {
          case Side.buy => orderbook.focus(_.bidLevelToQuantity).modify(_.updated(price, size))
          case Side.sell => orderbook.focus(_.askLevelToQuantity).modify(_.updated(price, size))
        }
      }
  }

  object L2Update {
    enum Side derives circe.Decoder {
      case buy, sell
    }
  }

  given circe.Decoder[Level2Message] =
    circe
      .Decoder[Snapshot]
      .or(circe.Decoder[L2Update].widen)

}

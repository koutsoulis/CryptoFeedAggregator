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
import monocle.syntax.all.*
import scala.util.Try
import io.scalaland.chimney.partial.Result
import scala.collection.immutable.TreeMap

sealed trait Level2Message

object Level2Message {

  case class Ignore(channel: ChannelToIgnore) extends Level2Message derives circe.Decoder

  given circe.Decoder[Level2Message] =
    circe
      .Decoder[Ignore].or(
        circe.Decoder[Relevant].widen
      )

  case class Relevant(
      events: List[Relevant.Event]
  ) extends Level2Message
      derives circe.Decoder

  object Relevant {

    case class Event(
        `type`: Event.Type,
        updates: List[Event.Update]
    ) derives circe.Decoder {
      def toOrderbook: Either[Exception, Orderbook] = this.`type` match {
        case Event.Type.snapshot =>
          Orderbook(
            bidLevelToQuantity = this
              .updates.filter(_.side == Event.Update.Side.bid).map { update => update.price_level -> update.new_quantity }
              .thrush(TreeMap.from),
            askLevelToQuantity = this
              .updates.filter(_.side == Event.Update.Side.offer).map { update => update.price_level -> update.new_quantity }
              .thrush(TreeMap.from)
          ).asRight

        case _ => Exception("attempted to transform Level2Message.Event of type other than `snapshot` to Orderbook").asLeft
      }
    }

    object Event {
      enum Type {
        case snapshot, update
      }
      given circe.Decoder[Type] = circe.Decoder.decodeString.emapTry { string => Try(Type.valueOf(string)) }

      case class Update(
          side: Update.Side,
          price_level: BigDecimal,
          new_quantity: BigDecimal
      ) derives circe.Decoder

      object Update {
        enum Side {
          case bid, offer
        }
        given circe.Decoder[Side] = circe.Decoder.decodeString.emapTry { string => Try(Side.valueOf(string)) }
      }
    }
  }
}

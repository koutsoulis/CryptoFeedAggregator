package servingRoutes.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import io.circe.syntax.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*

sealed trait ServerJSON

object ServerJSON {
  case class Orderbook(
      bidLevelToQuantity: List[(BigDecimal, BigDecimal)],
      askLevelToQuantity: List[(BigDecimal, BigDecimal)]
  ) extends ServerJSON
      derives circe.Encoder

  case class Candlestick(
      startTimeInMsSinceUnixEpoch: String,
      open: BigDecimal,
      close: BigDecimal,
      high: BigDecimal,
      low: BigDecimal
  ) extends ServerJSON
      derives circe.Encoder

  given circe.Encoder[ServerJSON] = circe.Encoder.instance {
    case a: Candlestick =>
      a.asJson
    case a: Orderbook => a.asJson
  }

  trait ToServerJSON[A] {
    def serverJSON(a: A): ServerJSON
  }

  // given chimney.Transformer[marketData.domain.Orderbook, Orderbook] = chimney.Transformer.derive[marketData.domain.Orderbook, Orderbook]
  /**
   * hacky domain -> dto transform with logic belonging to the frotnend, moved here cause I'm more familiar with scala than JS
   */
  given chimney.Transformer[marketData.domain.Orderbook, Orderbook] = {
    def scanAccumulateQuantityAlongLevels(list: List[(BigDecimal, BigDecimal)]) = list
      .tail
      .scanLeft[(BigDecimal, BigDecimal)](list.head) { case (_, accQuantity) -> (level, quantity) =>
        level -> (accQuantity + quantity)
      }

    chimney
      .Transformer.define[marketData.domain.Orderbook, Orderbook]
      .withFieldComputed(
        _.askLevelToQuantity,
        domainOrderbook => scanAccumulateQuantityAlongLevels(domainOrderbook.askLevelToQuantity.toList.take(50))
      )
      .withFieldComputed(
        _.bidLevelToQuantity,
        domainOrderbook => scanAccumulateQuantityAlongLevels(domainOrderbook.bidLevelToQuantity.toList.takeRight(50).reverse).reverse
      )
      .buildTransformer
  }

  given chimney.Transformer[marketData.domain.Candlestick, Candlestick] =
    chimney
      .Transformer.define[marketData.domain.Candlestick, Candlestick]
      .buildTransformer
}

package marketData.exchange.impl.binance.dto

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain

final case class Candlestick(
    k: Candlestick.K
) derives circe.Decoder

object Candlestick {
  final case class K(
      o: BigDecimal, // Open price
      c: BigDecimal, // Close price
      h: BigDecimal, // High price
      l: BigDecimal // Low price
  ) derives circe.Decoder

  given chimney.Transformer[Candlestick, domain.Candlestick] = chimney
    .Transformer.define[Candlestick, domain.Candlestick]
    .withFieldRenamed(_.k.c, _.close)
    .withFieldRenamed(_.k.h, _.high)
    .withFieldRenamed(_.k.o, _.open)
    .withFieldRenamed(_.k.l, _.low)
    .buildTransformer
}

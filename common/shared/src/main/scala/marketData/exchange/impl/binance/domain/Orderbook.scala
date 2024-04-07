package marketData.exchange.impl.binance.domain

import scala.collection.immutable.TreeMap
import cats.*
import cats.data.*
import cats.syntax.all.*
import io.bullet.borer
import io.bullet.borer.derivation.ArrayBasedCodecs.*

final case class Orderbook(
    lastUpdateId: Long,
    bidLevelToQuantity: TreeMap[BigDecimal, BigDecimal],
    askLevelToQuantity: TreeMap[BigDecimal, BigDecimal]
) derives borer.Codec

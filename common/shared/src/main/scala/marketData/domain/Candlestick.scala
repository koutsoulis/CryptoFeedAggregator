package marketData.domain

import _root_.io.bullet.borer
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*
import servingRoutes.dto.ServerJSON.ToServerJSON
import servingRoutes.dto.ServerJSON
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*

final case class Candlestick(
    startTimeInMsSinceUnixEpoch: String,
    open: BigDecimal,
    close: BigDecimal,
    high: BigDecimal,
    low: BigDecimal
) derives borer.Codec

object Candlestick {
  given ToServerJSON[Candlestick] = new ToServerJSON[Candlestick] {
    override def serverJSON(a: Candlestick) = a.transformInto[ServerJSON.Candlestick]
  }
}

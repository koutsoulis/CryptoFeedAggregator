package marketData.domain

import _root_.io.bullet.borer
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*

final case class Candlestick(
    startTimeInMsSinceUnixEpoch: String,
    open: BigDecimal,
    close: BigDecimal,
    high: BigDecimal,
    low: BigDecimal
) derives borer.Codec

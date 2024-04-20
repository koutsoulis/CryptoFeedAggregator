package marketData.names

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

case class TradePair(base: Currency, quote: Currency) derives circe.Codec.AsObject

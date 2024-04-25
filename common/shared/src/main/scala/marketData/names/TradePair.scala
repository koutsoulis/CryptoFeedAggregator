package marketData.names

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import _root_.io.bullet.borer
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*

case class TradePair(base: Currency, quote: Currency) derives circe.Codec.AsObject, borer.Codec

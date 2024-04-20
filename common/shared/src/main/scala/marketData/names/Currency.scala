package marketData.names

import _root_.io.bullet.borer
import _root_.io.bullet.borer.compat.scodec.*
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import java.util.Locale
import _root_.io.scalaland.chimney

case class Currency private (name: String) derives borer.Codec, circe.Codec.AsObject

object Currency {
  def apply(name: String): Currency = new Currency(name.toUpperCase(Locale.ROOT))

  given chimney.Transformer[String, Currency] = s => Currency(s)
}

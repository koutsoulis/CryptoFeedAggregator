package marketData

import marketData.exchange.impl.binance.domain.Orderbook
import org.http4s
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import _root_.io.scalaland.chimney
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import _root_.io.bullet.borer
import _root_.io.bullet.borer.compat.scodec.*
import _root_.io.bullet.borer.derivation.ArrayBasedCodecs.*
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s.ParseFailure
import org.http4s.QueryParamKeyLike
import org.http4s.QueryParam
import scodec.bits.ByteVector
import scodec.bits.Bases.Alphabets.Base64Url
import java.util.Locale
import marketData.names.Currency

// TODO: rename to MarketFeedName
sealed trait FeedName[M: borer.Encoder: borer.Decoder] {
  type Message = M

  def nameWithoutParametersForPrometheusLabelValue: String = this.getClass().getSimpleName()

  def parametersStringForPrometheusLabelValue: String

  def borerEncoderForMessage: borer.Encoder[M] = summon[borer.Encoder[M]]

  def borerDecoderForMessage: borer.Decoder[M] = summon[borer.Decoder[M]]
}

object FeedName {
  case class OrderbookFeed(currency1: Currency, currency2: Currency) extends FeedName[Orderbook] {
    override def parametersStringForPrometheusLabelValue: String = currency1.name ++ currency2.name
  }

  case class Stub(_value: Boolean = false) extends FeedName[Stub.Message] {
    override def parametersStringForPrometheusLabelValue: String = "stub"
  }

  object Stub {
    case class Message(value: Int) derives borer.Codec
  }

  given http4s.QueryParamCodec[marketData.FeedName[?]] = {

    given borer.Codec[FeedName[?]] = deriveAllCodecs[FeedName[?]]

    http4s
      .QueryParamCodec.from(
        decodeA = http4s
          .QueryParamDecoder.stringQueryParamDecoder.emap { string =>
            borer
              .Cbor.decode(ByteVector.fromValidBase64(string, Base64Url)).to[FeedName[?]].valueEither.left
              .map { err =>
                println(err.getMessage)
                ParseFailure(sanitized = err.getMessage, details = err.getMessage)
              }
          },
        encodeA = http4s.QueryParamEncoder.stringQueryParamEncoder.contramap { feedDef =>
          borer.Cbor.encode(feedDef).to[ByteVector].result.toBase64Url
        }
      )
  }

  object Matcher extends QueryParamDecoderMatcher[FeedName[?]]("feedName")
}

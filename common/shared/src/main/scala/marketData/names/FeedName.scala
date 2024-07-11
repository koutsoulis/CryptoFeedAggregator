package marketData.names

import marketData.domain.Orderbook
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
import marketData.domain.Candlestick

sealed trait FeedName[M: borer.Encoder: borer.Decoder] {
  type Message = M

  def nameWithoutParametersForPrometheusLabelValue: String = this.getClass().getSimpleName()

  def parametersStringForPrometheusLabelValue: String

  val borerEncoderForMessage: borer.Encoder[M] = summon[borer.Encoder[M]]

  val borerDecoderForMessage: borer.Decoder[M] = summon[borer.Decoder[M]]
}

object FeedName {
  type FeedNameQ = FeedName[?] // alias to avoid writing [?] when we don't want to specify the Message type

  case class OrderbookFeed(tradePair: TradePair) extends FeedName[Orderbook] {
    override val parametersStringForPrometheusLabelValue: String = tradePair.base.name ++ tradePair.quote.name
  }

  case class Candlesticks(tradePair: TradePair) extends FeedName[Candlestick] {
    override val parametersStringForPrometheusLabelValue: String = tradePair.base.name ++ tradePair.quote.name
  }

  given http4s.QueryParamCodec[FeedNameQ] = {

    given borer.Codec[FeedNameQ] = deriveAllCodecs[FeedNameQ]

    http4s
      .QueryParamCodec.from(
        decodeA = http4s
          .QueryParamDecoder.stringQueryParamDecoder.emap { string =>
            borer
              .Cbor.decode(ByteVector.fromValidBase64(string, Base64Url)).to[FeedNameQ].valueEither.left
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

  object Matcher extends QueryParamDecoderMatcher[FeedNameQ]("feedName")
}

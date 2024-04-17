package marketData

import marketData.exchange.impl.binance.domain.Orderbook
import org.http4s
import org.http4s.dsl.io.*
import org.http4s.implicits.*
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
// import scodec.*
// import scodec.bits.*
// import scodec.codecs.*

// TODO: rename to MarketFeedName
sealed trait FeedDefinition[M: borer.Encoder: borer.Decoder] {
  type Message = M

  def nameWithoutParametersForPrometheusLabelValue: String = this.getClass().getSimpleName()

  def parametersStringForPrometheusLabelValue: String

  def borerEncoderForMessage: borer.Encoder[M] = summon[borer.Encoder[M]]

  def borerDecoderForMessage: borer.Decoder[M] = summon[borer.Decoder[M]]
}

object FeedDefinition {
  case class OrderbookFeed(currency1: Currency, currency2: Currency) extends FeedDefinition[Orderbook] {
    override def parametersStringForPrometheusLabelValue: String = currency1.name ++ currency2.name
  }

  case class Stub(_value: Boolean = false) extends FeedDefinition[Stub.Message] {
    override def parametersStringForPrometheusLabelValue: String = "stub"
  }

  object Stub {
    case class Message(value: Int) derives borer.Codec
  }

  implicit val qpmC: http4s.QueryParamCodec[marketData.FeedDefinition[?]] = {

    given borer.Codec[FeedDefinition[?]] = deriveAllCodecs[FeedDefinition[?]]

    http4s
      .QueryParamCodec.from(
        decodeA = http4s
          .QueryParamDecoder.stringQueryParamDecoder.emap { string =>
            borer
              .Cbor.decode(ByteVector.fromValidBase64(string, Base64Url)).to[FeedDefinition[?]].valueEither.left
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

  // given queryEncoder: http4s.QueryParamEncoder[marketData.FeedDefinition[?]] = http4s.QueryParamEncoder.fromCodec

  object Matcher extends QueryParamDecoderMatcher[FeedDefinition[?]]("feedName")
}

case class Currency(name: String) derives borer.Codec, circe.Codec.AsObject

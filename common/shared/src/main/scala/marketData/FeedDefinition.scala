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

  // given borer.Codec[M] = borer.Codec.of
  // given borer.Codec[M] = summon[borer.Codec[M]]
}

object FeedDefinition {
  case class OrderbookFeed(currency1: Currency, currency2: Currency) extends FeedDefinition[Orderbook] {
    override def parametersStringForPrometheusLabelValue: String = currency1.name ++ currency2.name

    // override val borerCodecForMessage = implicitly[borer.Codec[Orderbook]]
  }

  case class Stub(_value: Boolean = false) extends FeedDefinition[Stub.Message] {
    override def parametersStringForPrometheusLabelValue: String = "stub"
  }

  object Stub {
    case class Message(value: Int) derives borer.Codec
  }

  // implicit val circeDecoder: circe.Decoder[FeedDefinition[?]] = List[circe.Decoder[FeedDefinition[?]]](
  //   circe.Decoder[OrderbookFeed].widen
  // ).reduceLeft(_ or _)

  given borer.Codec[FeedDefinition[?]] = deriveAllCodecs[FeedDefinition[?]]

  given http4s.QueryParamCodec[marketData.FeedDefinition[?]] =
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
          // implicit val asd: scodec.Encoder[FeedDefinition[?]] = ???
          // scodec.Encoder.encode(feedDef).require.toBase64Url
          // ???
          borer.Cbor.encode(feedDef).to[ByteVector].result.toBase64Url
        }
      )

  // implicit val cborDecoder: borer.Decoder[FeedDefinition[?]] = borer.Decoder

  // object FeedDefinitionQueryParamDecoderMatcher extends QueryParamDecoderMatcher[FeedDefinition[?]]("feedDefinition")
}

case class Currency(name: String) derives borer.Codec

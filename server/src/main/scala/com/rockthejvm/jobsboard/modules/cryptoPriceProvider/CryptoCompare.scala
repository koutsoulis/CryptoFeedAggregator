package com.rockthejvm.jobsboard.modules.cryptoPriceProvider

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.jdkhttpclient.JdkWSClient
import org.http4s.jdkhttpclient.JdkHttpClient
//circe
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import circe.Decoder
import org.http4s.client.websocket.WSRequest
import concurrent.duration.DurationInt
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary

import CryptoCompare.*
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.CryptoCompare.Dto.CryptoData
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.CryptoCompare.Dto.Ignore
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.CryptoCompare.Dto.UnrecoverableError

trait CryptoCompare[F[_]: Async] {
  def priceBTC: fs2.Stream[F, Dto.CryptoData]
}

// https://min-api.cryptocompare.com/documentation/websockets
// https://archive.ph/pBuKP

object CryptoCompare {

  val reconnectWait = 5

  def apply[F[_]: Async](client: http4s.client.websocket.WSClientHighLevel[F]): CryptoCompare[F] = {
    type WSStream = fs2.Stream[F, WSDataFrame]

    def reconnectingStream: fs2.Stream[F, org.http4s.client.websocket.WSDataFrame] = {

      val streamAcquireRelease: F[(WSStream, F[Unit])] =
        client
          .connectHighLevel(
            WSRequest(
              uri = uri""
            )).map(_.receiveStream).allocated

      val streamAcquireReleaseWithRetry = retry.retryingOnAllErrors(
        policy = retry.RetryPolicies.exponentialBackoff(5.seconds),
        onError = (_: Throwable, _) => Async[F].unit
      )(streamAcquireRelease)

      val resourceWrappingStream =
        Resource(streamAcquireReleaseWithRetry)

      fs2.Stream.resource(resourceWrappingStream).flatten ++
        (fs2.Stream.sleep(5.seconds) *> reconnectingStream)
    }

    new CryptoCompare[F] {
      override def priceBTC: fs2.Stream[F, Dto.CryptoData] = reconnectingStream.mapFilter{
        case Text(data, last) => 
          val message = circe.parser.decode[Dto.Message](data)
            .fold(err => throw new Exception(err), identity)
          message match {
            case cryptoData: CryptoData => Some(cryptoData)
            case Ignore => None
            case UnrecoverableError(contents) => throw new Exception(contents.toString())
          }

        case Binary(data, last) => throw new Exception("didnt expect binary WSFrame")
      }

    }
  }

  object Dto {
    sealed trait Message
    object Message{
      implicit val decoder: Decoder[Message] =
        List[Decoder[Message]](
          Decoder[CryptoData].widen,
          Decoder[UnrecoverableError].widen,
          Decoder[Ignore.type].widen,
        ).reduceLeft(_ or _)
    }
    // {"TYPE":"5","MARKET":"CCCAGG","FROMSYMBOL":"BTC","TOSYMBOL":"USD","FLAGS":2,"MEDIAN":49735.6637659428,"LASTTRADEID":"217093517","PRICE":49735.6637659428,"LASTUPDATE":1707832427,"LASTVOLUME":0.02800179,"LASTVOLUMETO":1392.2750404647,"VOLUMEHOUR":2593.3459684,"VOLUMEHOURTO":128681184.345007,"VOLUMEDAY":15494.02153829,"VOLUMEDAYTO":773812157.387051,"VOLUME24HOUR":49236.99141083,"VOLUME24HOURTO":2449291847.091117,"CURRENTSUPPLYMKTCAP":976108705309.5936,"CIRCULATINGSUPPLYMKTCAP":976108705309.5936,"MAXSUPPLYMKTCAP":1044448937935.905}
    case class CryptoData(
        TYPE: String,
        // MARKET: String,
        FROMSYMBOL: String,
        // TOSYMBOL: String,
        // FLAGS: Int,
        // MEDIAN: Double,
        // LASTTRADEID: String,
        PRICE: Double
        // LASTUPDATE: Long,
        // LASTVOLUME: Double,
        // LASTVOLUMETO: Double,
        // VOLUMEHOUR: Double,
        // VOLUMEHOURTO: Double,
        // VOLUMEDAY: Double,
        // VOLUMEDAYTO: Double,
        // VOLUME24HOUR: Double,
        // VOLUME24HOURTO: Double,
        // CURRENTSUPPLYMKTCAP: Double,
        // CIRCULATINGSUPPLYMKTCAP: Double,
        // MAXSUPPLYMKTCAP: Double
    ) extends Message derives Decoder

    object Ignore extends Message{
      implicit val decoder: Decoder[Ignore.type] = Decoder.const(Ignore)
    }

    /** Represents any kind of message from cryptodata following which we should drop
      * the websocket connection and do not attempt to reconnect
      *
      * Wraps a single Contents case class which reflects the decoded JSON object's shape.
      * 
      * @param contents
      */
    case class UnrecoverableError(
      contents: UnrecoverableError.Contents
    ) extends Message

    object UnrecoverableError{
      case class Contents(
        TYPE: Int,
        MESSAGE: String,
        INFO: String 
      ) derives Decoder

      implicit val decoder: circe.Decoder[UnrecoverableError] =
        Decoder[Contents].emap{contents => 
          Either.cond(
            test = unrecoverablePredicate(contents),
            right = UnrecoverableError(contents),
            left = "not unrecoverable"
          )
        }

      def unrecoverablePredicate(contents: Contents): Boolean = 
        contents.TYPE >= 400 && contents.TYPE < 600 && contents.MESSAGE != "FORCE_DISCONNECT"
    }

  }
}

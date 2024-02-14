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
import org.http4s.client.websocket.WSRequest
import concurrent.duration.DurationInt
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary

trait CryptoCompare[F[_]: Async] {
  def priceBTC: fs2.Stream[F, String]
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

      // TODO need to wait 5 seconds before the very first attempt too

      val resourceWrappingStream = Resource(streamAcquireReleaseWithRetry)

      fs2.Stream.resource(resourceWrappingStream).flatten.onComplete(reconnectingStream)
    }

    new CryptoCompare[F] {
      override def priceBTC: fs2.Stream[F, String] = reconnectingStream.map{
        case Text(data, last) => data
        case Binary(data, last) => "didnt expect binary frame"
      }

    }
  }

  object Dto {
    case class CryptoData(
        // TYPE: String,
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
    ) derives circe.Decoder

  }
}

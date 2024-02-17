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
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import circe.Decoder
import org.http4s.client.websocket.WSRequest
import concurrent.duration.DurationInt
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary

import cryptoCompare.dto.*
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.cryptoCompare.dto.Message.CryptoData
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.cryptoCompare.dto.Message.Ignore
import com.rockthejvm.jobsboard.modules.cryptoPriceProvider.cryptoCompare.dto.Message.UnrecoverableError

trait CryptoCompare[F[_]: Async] {
  def priceBTC: fs2.Stream[F, Message.CryptoData]
}

// https://min-api.cryptocompare.com/documentation/websockets
// https://archive.ph/pBuKP

object CryptoCompare {

  val reconnectWait = 5.seconds

  // TODO: tighter bounds than Async
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
        policy = retry.RetryPolicies.exponentialBackoff(reconnectWait),
        onError = (_: Throwable, _) => Async[F].unit
      )(streamAcquireRelease)

      val resourceWrappingStream =
        Resource(streamAcquireReleaseWithRetry)

      (fs2.Stream.sleep(reconnectWait) *> fs2.Stream.resource(resourceWrappingStream).flatten) ++
        reconnectingStream
    }

    new CryptoCompare[F] {
      override def priceBTC: fs2.Stream[F, Message.CryptoData] = {

        val textFrames: fs2.Stream[F, Text] = reconnectingStream
          .evalMapChunk {
            case textFrame: Text => Async[F].pure(textFrame)
            case Binary(data, last) => Async[F].raiseError(new Exception("didnt expect binary WSFrame"))
          }

        val serializedMessages = {
          def mergedFrames(frames: fs2.Stream[F, Text]): fs2.Stream[F, String] =
            frames.takeThrough(frame => !frame.last).map(_.data).foldMonoid ++
              mergedFrames(frames.dropThrough(frame => !frame.last))

          mergedFrames(textFrames)
        }

        val messagesWithoutErrors = serializedMessages
          .evalMap { serializedMessage =>
            circe.parser.decode[Message](serializedMessage).pure[F].rethrow
          }.evalMap[F, CryptoData | Ignore.type] {
            case data: CryptoData => Async[F].pure(data)
            case Ignore => Async[F].pure(Ignore)
            case UnrecoverableError(contents) => Async[F].raiseError(new Exception(contents.show))
          }

        messagesWithoutErrors.collect { case data: CryptoData => data }
      }
    }

  }
}

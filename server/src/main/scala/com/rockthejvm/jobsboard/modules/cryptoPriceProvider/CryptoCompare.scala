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
import fs2.Chunk

trait CryptoCompare[F[_]: Async] {
  def priceBTC: fs2.Stream[F, Message.CryptoData]
}

// https://min-api.cryptocompare.com/documentation/websockets
// https://archive.ph/pBuKP

object CryptoCompare {

  val reconnectWait = 5.seconds

  // TODO: tighter bounds than Async
  def apply[F[_]: Async](client: http4s.client.websocket.WSClientHighLevel[F]): CryptoCompare[F] = {

    new CryptoCompare[F] {
      override def priceBTC: fs2.Stream[F, Message.CryptoData] = {

        val streamAcquireRelease: F[(fs2.Stream[F, WSDataFrame], F[Unit])] =
          client
            .connectHighLevel(
              WSRequest(
                uri = uri""
              )).map(_.receiveStream).allocated

        val streamAcquireReleaseWithRetry = retry.retryingOnAllErrors(
          policy = retry.RetryPolicies.exponentialBackoff(reconnectWait * 2),
          onError = (_: Throwable, _) => Async[F].unit
        )(streamAcquireRelease)

        val resourceWrappingStream =
          Resource(streamAcquireReleaseWithRetry)

        val textFrames: fs2.Stream[F, Text] = fs2
          .Stream.resource(resourceWrappingStream).flatten
          .evalMap {
            case textFrame: Text => Async[F].pure(textFrame)
            case Binary(data, last) => Async[F].raiseError(new Exception("didnt expect binary WSFrame"))
          }

        val jsonMessages: fs2.Stream[F, String] = {
          val concatRelatedFrames: fs2.Scan[Vector[String], Text, String] = fs2.Scan.stateful(Vector.empty) { case (acc, frame) =>
            if (frame.last) {
              Vector.empty -> Chunk.singleton(acc.appended(frame.data).mkString)
            } else {
              acc.appended(frame.data) -> Chunk.empty
            }
          }

          textFrames.through(concatRelatedFrames.toPipe)
        }

        val messages: fs2.Stream[F, CryptoData] = jsonMessages
          .evalMap { serializedMessage =>
            circe.parser.decode[Message](serializedMessage).pure[F].rethrow
          }.evalMapFilter {
            case data: CryptoData => Async[F].pure(Some(data))
            case Ignore => Async[F].pure(None)
            case UnrecoverableError(contents) => Async[F].raiseError(new Exception(contents.show))
          }

        messages.delayBy(reconnectWait).repeat
      }
    }

  }
}

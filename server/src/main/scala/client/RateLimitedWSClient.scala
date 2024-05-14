package client

import _root_.io.circe
import _root_.io.circe.Json
import cats.*
import cats.effect.*
import cats.syntax.all.*
import client.rateLimits.RLSemaphoreAndReleaseTime
import fs2.Stream
import org.http4s
import org.http4s.Uri
import org.http4s.client.websocket
import org.http4s.client.websocket.WSRequest
import org.typelevel.log4cats.Logger

trait RateLimitedWSClient[F[_]: Async] {
  def wsConnect[Out: circe.Decoder](uri: Uri, subscriptionMessages: Seq[Json] = Seq.empty): Stream[F, Out]
}

object RateLimitedWSClient {
  class RateLimitedWSCLientLive[F[_]: Logger](
      wsClient: websocket.WSClientHighLevel[F],
      wsEstablishConnectionRL: RLSemaphoreAndReleaseTime[F]
  )(
      using F: Async[F]
  ) extends RateLimitedWSClient {

    /**
     * Assumption: ws market stream messages are guaranteed to arrive in order
     *
     * Starts counting down to release the permits after the websocket connection has been established. It would be more pragmatic (easier
     * to express, reason about and correct enough) if the countdown started right before emitting the request to establish the connection ,
     * by also incrementing the time to countdown by say ~2secs to account for possible high latencies. And if the 2 secs are not enough and
     * we hit the rate limits not bother recover.
     *
     * @param uri
     * @return
     */
    override def wsConnect[Out: circe.Decoder](uri: Uri, subscriptionMessages: Seq[Json]): Stream[F, Out] = {
      val establishWSConnection = F.bracketFull { poll =>
        Logger[F].debug(s"ws connect attempt to: $uri") *>
          poll(wsEstablishConnectionRL.semaphore.acquire)
      } { wsRequest =>
        wsClient.connectHighLevel(WSRequest(uri)).allocatedCase
      } { (_, outcome) =>
        Logger[F].debug(s"ws attempt to: $uri , outcome: $outcome") *>
          F.start(F.sleep(wsEstablishConnectionRL.releaseTime) *> wsEstablishConnectionRL.semaphore.release).void
      }

      Stream
        .resource(
          Resource.applyFull[F, websocket.WSConnectionHighLevel[F]] { poll => poll(establishWSConnection) }
        ).flatMap { conn =>
          Stream.exec(
            subscriptionMessages.traverse_ { message =>
              conn.sendText(message.toString) <* Logger[F].debug(s"subscriptionMessage: ${message.toString}")
            }
          ) ++ conn.receiveStream
        }
        .evalMapChunk {
          case websocket.WSFrame.Text(data, _) =>
            Logger[F].debug(s"received ${data.take(200)} from $uri") *>
              F.fromEither(circe.parser.decode[Out](data))
          case _: websocket.WSFrame.Binary =>
            F.raiseError(new Exception(s"Expected text but received binary ws frame while consuming stream of $uri"))
        }
    }
  }

  def apply[F[_]: Async: Logger](
      wsClient: websocket.WSClientHighLevel[F],
      wsEstablishConnectionRL: RLSemaphoreAndReleaseTime[F]
  ): RateLimitedWSClient[F] = RateLimitedWSCLientLive(wsClient, wsEstablishConnectionRL)
}

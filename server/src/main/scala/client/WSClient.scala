package client

import client.rateLimits.RLSemaphoreAndReleaseTime
import _root_.io.circe
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.client.websocket
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.Uri
import fs2.Stream
import org.typelevel.log4cats.Logger
import _root_.io.circe.Json

trait WSClient[F[_]: Async] {
  def wsConnect[Out: circe.Decoder](uri: String, subscriptionMessages: Seq[Json] = Seq.empty): Stream[F, Out]
}

object WSClient {
  class WSCLientLive[F[_]: Logger](
      wsClient: websocket.WSClientHighLevel[F],
      wsEstablishConnectionRL: RLSemaphoreAndReleaseTime[F]
  )(
      using F: Async[F]
  ) extends WSClient {

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
    override def wsConnect[Out: circe.Decoder](uri: String, subscriptionMessages: Seq[Json]): Stream[F, Out] = {
      val establishWSConnection = F.bracketFull { poll =>
        Logger[F].debug(s"ws connect attempt to: $uri") *>
          F.fromEither(Uri.fromString(uri)).map(websocket.WSRequest.apply) <*
          poll(wsEstablishConnectionRL.semaphore.acquire)
      } { wsRequest =>
        wsClient.connectHighLevel(wsRequest).allocatedCase
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
  ): WSClient[F] = WSCLientLive(wsClient, wsEstablishConnectionRL)
}

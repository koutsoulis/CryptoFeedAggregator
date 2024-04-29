package marketData.exchange.impl.coinbase

import org.http4s.client.websocket.WSClientHighLevel
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import fs2.{Stream, Pull}
import client.WSClient
import marketData.names.TradePair
import org.http4s.implicits.uri
import marketData.exchange.impl.coinbase.dto.WSMessage.Level2Message
import marketData.exchange.impl.coinbase.dto.WSMessage
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Orderbook
import client.rateLimits.RLSemaphoreAndReleaseTime
import cats.effect.std.Semaphore
import org.typelevel.log4cats.Logger
import marketData.exchange.impl.coinbase.dto.SubscribeRequest
import marketData.names.FeedName
import _root_.io.circe
import marketData.names.FeedName.OrderbookFeed
import marketData.exchange.impl.coinbase.dto.WSMessage.Level2Message.Event.Update.Side
import monocle.syntax.all.*

class Client[F[_]] private (
    wsClient: WSClient[F]
)(using F: Async[F]) {
  private val baseURI = uri"wss://advanced-trade-ws.coinbase.com"

  def orderbook(feedName: OrderbookFeed): Stream[F, marketData.domain.Orderbook] = {

    val subscribeRequest = circe
      .Encoder.apply[SubscribeRequest].apply(
        SubscribeRequest(
          feedName = feedName,
          channel = "level2"
        )
      ).toString

    wsClient
      .wsConnect[WSMessage](uri = baseURI.renderString, subscriptionMessage = Some(subscribeRequest))
      .collect { case m: Level2Message => m }
      .map(_.events)
      .flatMap(Stream.emits)
      .pull.uncons1.flatMap {
        case Some((firstEvent, subsequentEvents)) =>
          val updatesTail = subsequentEvents.evalTapChunk { event =>
            F.raiseWhen(event.`type` != Level2Message.Event.Type.update)(
              new Exception(s"Level2Message Event of type ${event.`type`} encountered past the head")
            )
          }
          val orderbooksStream = firstEvent.toOrderbook match {
            case Left(exception) => Stream.raiseError(exception)
            case Right(orderbook) =>
              updatesTail.scan(orderbook) { case (orderbook, updateEvent) =>
                updateEvent.updates.foldLeft(orderbook) { case (orderbook, update) =>
                  update.side match {
                    case Side.bid =>
                      orderbook
                        .focus(_.bidLevelToQuantity).modify(_.updatedWith(update.price_level) { _ =>
                          Some(update.new_quantity).filter(_ > 0)
                        })
                    case Side.offer =>
                      orderbook
                        .focus(_.askLevelToQuantity).modify(_.updatedWith(update.price_level) { _ =>
                          Some(update.new_quantity).filter(_ > 0)
                        })
                  }
                }
              }
          }
          orderbooksStream.pull.echo

        case _unexpected => Pull.raiseError(new Exception(s"unexpected case ${_unexpected.toString().take(200)}"))
      }.stream
  }
}

object Client {
  def apply[F[_]: Async: Logger](wsClient: WSClientHighLevel[F]): F[Client[F]] = {
    Semaphore(constants.websocketRequestsPerSecondPerIP)
      .map { sem =>
        RLSemaphoreAndReleaseTime(semaphore = sem, releaseTime = constants.websocketRateLimitRefreshPeriod)
      }.map { wsEstablishConnectionRL =>
        WSClient
          .apply(
            wsClient = wsClient,
            wsEstablishConnectionRL = wsEstablishConnectionRL
          )
      }.map { wsClient => new Client(wsClient) }
  }
}

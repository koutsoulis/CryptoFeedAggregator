package marketData.exchange.impl.coinbase

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import fs2.{Stream, Pull}
import client.RateLimitedWSClient
import client.RateLimitedHttpClient
import marketData.names.TradePair
import org.http4s.implicits.uri
import org.http4s
import marketData.exchange.impl.coinbase.dto.Level2Message.Relevant
import marketData.exchange.impl.coinbase.dto.Level2Message
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Orderbook
import client.rateLimits.RLSemaphoreAndReleaseTime
import cats.effect.std.Semaphore
import org.typelevel.log4cats.Logger
import marketData.exchange.impl.coinbase.dto.SubscribeRequest
import marketData.names.FeedName
import _root_.io.circe
import marketData.exchange.impl.coinbase.dto.Level2Message.Relevant.Event.Update.Side
import monocle.syntax.all.*
import client.RateLimitedHttpClient.RateLimitedHttpClientLive
import marketData.exchange.impl.coinbase.dto.ListProducts
import marketData.names.Currency

class Client[F[_]] private (
    wsClient: RateLimitedWSClient[F],
    httpClient: RateLimitedHttpClient[F]
)(using F: Async[F]) {
  def orderbook(feedName: FeedName.OrderbookFeed): Stream[F, marketData.domain.Orderbook] = {

    val subscribeRequests =
      SubscribeRequest
        .relevantAndHeartbeats(
          feedName = feedName
        ).map(circe.Encoder.apply[SubscribeRequest].apply)

    wsClient
      .wsConnect[Level2Message](uri = constants.advancedTradeWebSocketEndpoint.renderString, subscriptionMessages = subscribeRequests)
      .collect { case m: Relevant => m }
      .map(_.events)
      .flatMap(Stream.emits)
      .pull.uncons1.flatMap {
        case Some((firstEvent, subsequentEvents)) =>
          val updatesTail = subsequentEvents.evalTapChunk { event =>
            F.raiseWhen(event.`type` != Relevant.Event.Type.update)(
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

  def candlesticks(feedName: FeedName.Candlesticks): Stream[F, marketData.domain.Candlestick] = {
    val subscribeRequests = SubscribeRequest
      .relevantAndHeartbeats(
        feedName = feedName
      ).map(circe.Encoder.apply[SubscribeRequest].apply)

    wsClient
      .wsConnect[dto.CandlesMessage](
        uri = constants.advancedTradeWebSocketEndpoint.renderString,
        subscriptionMessages = subscribeRequests
      ).collect { case relevant: dto.CandlesMessage.Relevant => relevant }
      .map(_.events.lastOption) // if more than one, consider all but last one out of date
      .flattenOption
      .map(_.candles.lastOption) // if more than one, consider all but last one out of date
      .flattenOption
      .map(_.transformInto[marketData.domain.Candlestick])
  }

  def enabledTradePairs: F[List[TradePair]] = httpClient
    .get[ListProducts](
      uri = constants.advancedTradeEndpointURL.addPath("market/products").toString,
      permitsNeeded = 1
    ).map(_.products)
    .map { products =>
      products
        .filter(!_.is_disabled).map { product => Currency(product.base_currency_id) -> Currency(product.quote_currency_id) }
        .map(TradePair.apply)
    }
}

object Client {
  def apply[F[_]: Async: Logger](
      wsClient: http4s.client.websocket.WSClientHighLevel[F],
      http4sHttpClient: http4s.client.Client[F]
  ): F[Client[F]] = {
    val wsClientWrapped: F[RateLimitedWSClient[F]] = Semaphore(constants.websocketRequestsPerSecondPerIP)
      .map { sem =>
        RLSemaphoreAndReleaseTime(semaphore = sem, releaseTime = constants.websocketRateLimitRefreshPeriod)
      }.map { wsEstablishConnectionRL =>
        RateLimitedWSClient
          .apply(
            wsClient = wsClient,
            wsEstablishConnectionRL = wsEstablishConnectionRL
          )
      }

    val httpClientWrapped: F[RateLimitedHttpClient[F]] = Semaphore(constants.httpRequestsPerSecondPerIP)
      .map { sem =>
        RLSemaphoreAndReleaseTime(semaphore = sem, releaseTime = constants.httpRateLimitRefreshPeriod)
      }.map { rateLimitsData =>
        RateLimitedHttpClientLive(
          httpClient = http4sHttpClient,
          rateLimitsData = rateLimitsData
        )
      }

    (wsClientWrapped, httpClientWrapped).mapN { case (wsClientWrapped, httpClientWrapped) =>
      new Client(
        wsClient = wsClientWrapped,
        httpClient = httpClientWrapped
      )
    }
  }
}

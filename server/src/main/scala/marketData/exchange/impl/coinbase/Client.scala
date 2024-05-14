package marketData.exchange.impl.coinbase

import _root_.io.circe
import cats.*
import cats.effect.*
import cats.effect.std.Semaphore
import cats.syntax.all.*
import client.RateLimitedHttpClient
import client.RateLimitedHttpClient.RateLimitedHttpClientLive
import client.RateLimitedWSClient
import client.rateLimits.RLSemaphoreAndReleaseTime
import fs2.Stream
import marketData.exchange.impl.coinbase.dto.CandlesMessage
import marketData.exchange.impl.coinbase.dto.Level2Message
import marketData.exchange.impl.coinbase.dto.ListProducts
import marketData.exchange.impl.coinbase.dto.SubscribeRequest
import marketData.names.Currency
import marketData.names.FeedName
import marketData.names.FeedName.Candlesticks
import marketData.names.FeedName.OrderbookFeed
import marketData.names.TradePair
import org.http4s
import org.typelevel.log4cats.Logger

trait Client[F[_]: Async] {
  def level2Messages(feedName: FeedName.OrderbookFeed): fs2.Stream[F, dto.Level2Message]
  def candlesticks(feedName: FeedName.Candlesticks): Stream[F, dto.CandlesMessage]
  def enabledTradePairs: F[List[TradePair]]
}

object Client {
  class ClientLive[F[_]: Async] private[Client] (
      wsClient: RateLimitedWSClient[F],
      httpClient: RateLimitedHttpClient[F]
  ) extends Client {

    override def level2Messages(feedName: FeedName.OrderbookFeed): fs2.Stream[F, dto.Level2Message] = {
      val subscribeRequests =
        SubscribeRequest
          .relevantAndHeartbeats(
            feedName = feedName
          ).map(circe.Encoder.apply[SubscribeRequest].apply)

      wsClient
        .wsConnect[dto.Level2Message](uri = constants.advancedTradeWebSocketEndpoint, subscriptionMessages = subscribeRequests)
    }

    override def candlesticks(feedName: FeedName.Candlesticks): Stream[F, dto.CandlesMessage] = {
      val subscribeRequests = SubscribeRequest
        .relevantAndHeartbeats(
          feedName = feedName
        ).map(circe.Encoder.apply[SubscribeRequest].apply)

      wsClient
        .wsConnect[dto.CandlesMessage](
          uri = constants.advancedTradeWebSocketEndpoint,
          subscriptionMessages = subscribeRequests
        )
    }

    override def enabledTradePairs: F[List[TradePair]] = httpClient
      .get[ListProducts](
        uri = constants.listPublicProductsEndpoint,
        permitsNeeded = 1
      ).map(_.products)
      .map { products =>
        products
          .filter(!_.is_disabled).map { product => Currency(product.base_currency_id) -> Currency(product.quote_currency_id) }
          .map(TradePair.apply)
      }
  }

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
      new ClientLive(
        wsClient = wsClientWrapped,
        httpClient = httpClientWrapped
      )
    }
  }

  def stub[F[_]](using Async[F])(
      level2MessagesStub: (feedName: FeedName.OrderbookFeed) => fs2.Stream[F, dto.Level2Message] = _ => ???,
      candlesticksStub: (feedName: FeedName.Candlesticks) => Stream[F, dto.CandlesMessage] = _ => ???,
      enabledTradePairsStub: F[List[TradePair]] = List.empty.pure
  ): Client[F] = new Client {

    override def level2Messages(feedName: OrderbookFeed): Stream[F, Level2Message] = level2MessagesStub(feedName)

    override def candlesticks(feedName: Candlesticks): Stream[F, CandlesMessage] = candlesticksStub(feedName)

    override def enabledTradePairs: F[List[TradePair]] = enabledTradePairsStub

  }
}

package marketData.exchange.impl

import _root_.io.scalaland.chimney.syntax.*
import cats.*
import cats.effect.*
import cats.effect.std.Semaphore
import cats.syntax.all.*
import fs2.Pull
import fs2.Stream
import marketData.exchange.Exchange
import marketData.exchange.impl.binance.domain.Orderbook
import marketData.exchange.impl.binance.domain.OrderbookUpdate
import marketData.exchange.impl.binance.domain.RateLimits
import marketData.exchange.impl.binance.dto.ExchangeInfo
import marketData.names.FeedName
import marketData.names.FeedName.Candlesticks
import marketData.names.FeedName.OrderbookFeed
import marketData.names.TradePair
import names.ExchangeName
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.log4cats.Logger

import binance.dto
import binance.constants

class Binance[F[_]] private (
    client2: binance.Client[F],
    override val allCurrencyPairs: List[TradePair]
)(
    using F: Async[F]
) extends Exchange[F] {

  override def activeCurrencyPairs: F[List[TradePair]] = client2.activeCurrencyPairs

  override def name: ExchangeName = ExchangeName.Binance

  override def stream[M](feedName: FeedName[M]): Stream[F, M] = feedName match {
    case orderbookFeedDef: FeedName.OrderbookFeed => orderbookStream(orderbookFeedDef)
    case Candlesticks(tradePair) => client2.candlesticks(tradePair)
  }

  // Assumption: ws market stream messages are guaranteed to arrive in order
  private def orderbookStream(level2Def: FeedName.OrderbookFeed): Stream[F, marketData.domain.Orderbook] = level2Def match {
    case OrderbookFeed(tradePair) =>
      /**
       * We assume there are no messages missing or out of order
       */
      val orderbookUpdates: Stream[F, OrderbookUpdate] = client2.orderbookUpdates(tradePair)

      val orderbookSnapshot: F[Orderbook] = client2.orderbookSnapshot(tradePair)

      val orderbookSnapshotsAsNestedPull = for {
        firstUpdateMaybe <- orderbookUpdates.pull.uncons1
        (firstUpdate, rest) <- Pull.eval(F.fromOption(firstUpdateMaybe, new Exception))
        snapshotIssuedAfterFirstUpdate <- Pull.eval(orderbookSnapshot)
        relevantUpdates = rest.dropWhile(_.lastUpdateId <= snapshotIssuedAfterFirstUpdate.lastUpdateId)
        orderbookSnapshots = relevantUpdates
          .scan(snapshotIssuedAfterFirstUpdate) { case (snapshot, update) =>
            update.update(snapshot)
          }.pull.echo
      } yield orderbookSnapshots

      orderbookSnapshotsAsNestedPull.flatten.stream.map(_.transformInto[marketData.domain.Orderbook])
  }
}

object Binance {
  def apply[F[_]: Logger](
      http4sHttpClient: http4s.client.Client[F],
      wsClient: http4s.client.websocket.WSClientHighLevel[F]
  )(
      using F: Async[F]
  ): F[Binance[F]] = {
    for {
      exchangeInfo <- http4sHttpClient
        .expect[dto.ExchangeInfo](constants.exchangeInfoEndpoint)
      RateLimits(requestWeight, _) <- F.fromEither(RateLimits.of(exchangeInfo))

      httpRateLimitSem <- Semaphore(requestWeight.permitsAvailable - constants.exchangeInfoRequestWeight)
      wsConnectionRateLimitSem <- Semaphore(constants.wsConnectionPermits)
      binanceHttpClient = client
        .RateLimitedHttpClient.RateLimitedHttpClientLive(
          httpClient = http4sHttpClient,
          rateLimitsData = client
            .rateLimits.RLSemaphoreAndReleaseTime(
              semaphore = httpRateLimitSem,
              releaseTime = requestWeight.timeToReleasePermits
            )
        )
      binanceWSClient = client
        .RateLimitedWSClient.RateLimitedWSCLientLive(
          wsClient = wsClient,
          wsEstablishConnectionRL = client
            .rateLimits.RLSemaphoreAndReleaseTime(
              semaphore = wsConnectionRateLimitSem,
              releaseTime = constants.wsConnectionPermitReleaseTime
            )
        )
    } yield new Binance(
      client2 = binance.Client(binanceHttpClient, binanceWSClient),
      allCurrencyPairs = exchangeInfo.symbols.map(_.transformInto[TradePair])
    )
  }
}

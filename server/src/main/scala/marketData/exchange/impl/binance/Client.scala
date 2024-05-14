package marketData.exchange.impl.binance

import _root_.io.scalaland.chimney.syntax.*
import cats.*
import cats.effect.*
import cats.syntax.all.*
import client.RateLimitedHttpClient
import client.RateLimitedWSClient
import fs2.Stream
import marketData.exchange.impl.binance.domain.Orderbook
import marketData.exchange.impl.binance.dto.ExchangeInfo
import marketData.exchange.impl.binance.dto.ExchangeInfo.SymbolPair.Status
import marketData.names.TradePair

class Client[F[_]](
    httpClient: RateLimitedHttpClient[F],
    wsClient: RateLimitedWSClient[F]
)(using F: Async[F]) {

  def activeCurrencyPairs: F[List[TradePair]] = httpClient
    .get[ExchangeInfo](
      uri = constants.exchangeInfoEndpoint,
      permitsNeeded = constants.exchangeInfoRequestWeight
    )
    .map(
      _.symbols
        .filter(_.status == Status.TRADING)
        .map { pair => pair.transformInto[TradePair] }
    )

  def orderbookSnapshot(tradePair: TradePair): F[Orderbook] =
    httpClient
      .get[dto.Orderbook](
        constants.orderbookSnapshotEndpoint(tradePair),
        constants.orderbookSnapshotRLPermits
      )
      .map(dto.Orderbook.transformer.transform)

  def orderbookUpdates(tradePair: TradePair): Stream[F, domain.OrderbookUpdate] =
    wsClient
      .wsConnect[dto.OrderbookUpdate](
        constants.diffDepthStreamEndpoint(tradePair)
      ).map(domain.OrderbookUpdate.transformer.transform)
      .evalTap(out => F.delay(println(out.lastUpdateId)))

  def candlesticks(tradePair: TradePair): Stream[F, marketData.domain.Candlestick] =
    wsClient
      .wsConnect[dto.Candlestick](
        constants.candlestickStreamEndpoint(tradePair)
      ).map(_.transformInto[marketData.domain.Candlestick])

}

package marketData.exchange.impl

import marketData.exchange.ExchangeSpecific
import marketData.names.FeedName
import marketData.names.Currency
import org.http4s.client.websocket.WSRequest
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Stub
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.Uri
import org.http4s.Request
import org.http4s.dsl.*
import org.http4s.implicits.*
import cats.MonadThrow
import scala.concurrent.duration.*
import binance.dto
import marketData.exchange.impl.binance.domain.RateLimits
import cats.effect.std.Semaphore
import fs2.{Stream, Pull}
import fs2.concurrent.Signal
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import _root_.io.circe
import marketData.exchange.impl.binance.domain.{OrderbookUpdate, Orderbook, RateLimits}
import binance.dto.ExchangeInfo.SymbolPair.Status
import marketData.names.TradePair
import marketData.exchange.impl.binance.dto.ExchangeInfo
import _root_.io.scalaland.chimney.syntax.*
import marketData.names.FeedName.Candlesticks
import marketData.exchange.impl.binance.dto.Candlestick
import org.typelevel.log4cats.Logger

trait Binance[F[_]] private (
    client2: binance.Client[F]
)(
    implicit F: Async[F]
) extends ExchangeSpecific[F] {

  override def stream[M](feedDef: FeedName[M]): Stream[F, M] = feedDef match {
    case orderbookFeedDef: FeedName.OrderbookFeed => orderbookStream(orderbookFeedDef)
    case Candlesticks(tradePair) => client2.candlesticks(tradePair)
    case Stub(_value) => ???
  }

  // Assumption: ws market stream messages are guaranteed to arrive in order
  private def orderbookStream(level2Def: FeedName.OrderbookFeed): Stream[F, Orderbook] = level2Def match {
    case OrderbookFeed(tradePair) =>
      /**
       * We assume there are no messages missing or out of order
       */
      val orderbookUpdates: Stream[F, OrderbookUpdate] = client2.orderbookUpdates(tradePair)

      val orderbookSnapshot: F[Orderbook] = client2.orderbookSnapshot(tradePair)

      // TODO check if expressible using Stream#debounce
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

      orderbookSnapshotsAsNestedPull.flatten.stream
  }
}

object Binance {
  val baseEndpoint = uri"https://api.binance.com"
  val exchangeInfoUriSegment: String = "api/v3/exchangeInfo"
  val exchangeInfoRequestWeight = 20
  val wsConnectionPermits = 300
  val wsConnectionPermitReleaseTime = 5.minutes
  def symbol(currency: Currency): String = currency.name
  def streamSymbol(currency: Currency): String = currency.name.toLowerCase()
  def tradePairSymbol(pair: TradePair): String = symbol(pair.base) ++ symbol(pair.quote)
  def streamTradePairSymbol(pair: TradePair): String = streamSymbol(pair.base) ++ streamSymbol(pair.quote)

  def apply[F[_]: Logger](
      http4sHttpClient: http4s.client.Client[F],
      wsClient: http4s.client.websocket.WSClientHighLevel[F]
  )(
      using F: Async[F]
  ): F[Binance[F]] = {
    for {
      exchangeInfo <- http4sHttpClient
        .expect[dto.ExchangeInfo](baseEndpoint.addPath(exchangeInfoUriSegment))
      RateLimits(requestWeight, _) <- F.fromEither(RateLimits.of(exchangeInfo))

      httpRateLimitSem <- Semaphore(requestWeight.permitsAvailable - exchangeInfoRequestWeight)
      wsConnectionRateLimitSem <- Semaphore(wsConnectionPermits)
      binanceHttpClient = client
        .HttpClient.HttpClientLive(
          httpClient = http4sHttpClient,
          rateLimitsData = client
            .rateLimits.RLSemaphoreAndReleaseTime(
              semaphore = httpRateLimitSem,
              releaseTime = requestWeight.timeToReleasePermits
            )
        )
      binanceWSClient = client
        .WSClient.WSCLientLive(
          wsClient = wsClient,
          wsEstablishConnectionRL = client
            .rateLimits.RLSemaphoreAndReleaseTime(
              semaphore = wsConnectionRateLimitSem,
              releaseTime = wsConnectionPermitReleaseTime
            )
        )
    } yield new Binance(binance.Client(binanceHttpClient, binanceWSClient)) {

      override def activeCurrencyPairs: F[List[TradePair]] =
        binanceHttpClient
          .get[ExchangeInfo](baseEndpoint.addPath(exchangeInfoUriSegment).renderString, exchangeInfoRequestWeight)
          .map(
            _.symbols
              .filter(_.status == Status.TRADING)
              .map { pair => pair.transformInto[TradePair] }
          )

      override def allCurrencyPairs: List[TradePair] =
        exchangeInfo.symbols.map(_.transformInto[TradePair])
    }
  }
}

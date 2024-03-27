package marketData.exchange.impl.binance

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.Currency
import marketData.exchange.impl.binance.domain.Orderbook
import cats.effect.*
import org.http4s.client.websocket
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import scala.concurrent.duration.Duration
import cats.effect.std.Semaphore
import org.http4s.Uri
import marketData.exchange.impl.Binance
import _root_.io.circe
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Canceled
import org.http4s.client.websocket.WSRequest
import fs2.Stream
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import client.HttpClient
import client.WSClient

class Client[F[_]](
    httpClient: HttpClient[F],
    wsClient: WSClient[F]
)(using F: Async[F]) {
  def orderbookSnapshot(currency1: Currency, currency2: Currency): F[Orderbook] =
    httpClient
      .get[dto.Orderbook](
        s"https://api.binance.com/api/v3/depth?symbol=${Binance.tradePairSymbol(currency1, currency2)}&limit=5000",
        250
      )
      .map(domain.Orderbook.transformer.transform)

  def orderbookUpdates(currency1: Currency, currency2: Currency): Stream[F, domain.OrderbookUpdate] =
    wsClient
      .wsConnect[dto.OrderbookUpdate](
        s"wss://stream.binance.com:9443/ws/${Binance.streamTradePairSymbol(currency1, currency2)}@depth@100ms"
      ).map(domain.OrderbookUpdate.transformer.transform)

}

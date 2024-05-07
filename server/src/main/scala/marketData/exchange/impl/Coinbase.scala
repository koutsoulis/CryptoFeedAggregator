package marketData.exchange.impl

import marketData.exchange.Exchange
import cats.effect.*
import marketData.names.FeedName
import marketData.names.TradePair
import marketData.names.FeedName.OrderbookFeed
import marketData.names.FeedName.Candlesticks
import marketData.names.Currency
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import org.typelevel.log4cats.Logger
import names.ExchangeName
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import marketData.exchange.impl.coinbase.dto.ListProducts

class Coinbase[F[_]: Async] private (
    client: coinbase.Client[F],
    override val allCurrencyPairs: List[TradePair]
) extends Exchange[F] {
  override def activeCurrencyPairs: F[List[TradePair]] = client.enabledTradePairs

  override def stream[M](feedDef: FeedName[M]): fs2.Stream[F, M] = feedDef match {
    case feedName: OrderbookFeed => client.orderbook(feedName)
    case feedName: Candlesticks => client.candlesticks(feedName)
  }

  override def name: ExchangeName = ExchangeName.Coinbase

}

object Coinbase {
  def apply[F[_]: Logger](
      wsClient: http4s.client.websocket.WSClientHighLevel[F],
      http4sHttpClient: http4s.client.Client[F]
  )(using F: Async[F]): F[Coinbase[F]] = {
    val tradePairs = http4sHttpClient
      .expect[ListProducts](coinbase.constants.advancedTradeEndpointURL.addPath("market/products"))
      .map(_.products)
      .map { products =>
        products
          .map { product => Currency(product.base_currency_id) -> Currency(product.quote_currency_id) }
          .map(TradePair.apply)
      }

    val coinbaseClient = coinbase
      .Client(
        wsClient = wsClient,
        http4sHttpClient = http4sHttpClient
      )

    (tradePairs, coinbaseClient)
      .mapN { (tradePairs, coinbaseClient) =>
        new Coinbase(coinbaseClient, tradePairs)
      }
  }
}

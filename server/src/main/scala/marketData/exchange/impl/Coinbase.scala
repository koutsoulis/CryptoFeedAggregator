package marketData.exchange.impl

import marketData.exchange.ExchangeSpecific
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
import org.http4s.client.websocket.WSClientHighLevel
import org.typelevel.log4cats.Logger
import names.ExchangeName

trait Coinbase[F[_]: Async] extends ExchangeSpecific[F] {}

object Coinbase {
  class CoinbaseLive[F[_]: Async] private[Coinbase] (
      client: coinbase.Client[F]
  ) extends Coinbase {

    override def allCurrencyPairs: List[TradePair] = List(TradePair(Currency("BTC"), Currency("USD")))

    override def activeCurrencyPairs: F[List[TradePair]] = allCurrencyPairs.pure

    override def stream[M](feedDef: FeedName[M]): fs2.Stream[F, M] = feedDef match {
      case feedName: OrderbookFeed => client.orderbook(feedName)
      case Candlesticks(tradePair) => ???
    }

    override def name: ExchangeName = ExchangeName.Coinbase

  }

  def apply[F[_]: Logger](wsClient: WSClientHighLevel[F])(using F: Async[F]) =
    coinbase.Client(wsClient = wsClient).map(new CoinbaseLive(_))

}

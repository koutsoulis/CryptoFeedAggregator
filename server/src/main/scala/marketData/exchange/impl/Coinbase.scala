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
import marketData.exchange.impl.coinbase.dto.Level2Message
import fs2.{Stream, Pull}
import marketData.exchange.impl.coinbase.dto.Level2Message.Relevant.Event.Update.Side
import marketData.exchange.impl.coinbase.dto
import marketData.domain.Orderbook
import monocle.syntax.all.*
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Candlestick

class Coinbase[F[_]: Async] private (
    client: coinbase.Client[F],
    override val allCurrencyPairs: List[TradePair]
) extends Exchange[F] {
  override def activeCurrencyPairs: F[List[TradePair]] = client.enabledTradePairs

  override def stream[M](feedName: FeedName[M]): fs2.Stream[F, M] = feedName match {
    case feedName: OrderbookFeed => orderbookStream(feedName)
    case feedName: Candlesticks => candlestickStream(feedName)
  }

  override def name: ExchangeName = ExchangeName.Coinbase

  private def candlestickStream(feedName: Candlesticks): Stream[F, marketData.domain.Candlestick] = client
    .candlesticks(feedName)
    .collect { case relevant: dto.CandlesMessage.Relevant => relevant }
    .map(_.events.lastOption) // if more than one, consider all but last one out of date
    .flattenOption
    .map(_.candles.lastOption) // if more than one, consider all but last one out of date
    .flattenOption
    .map(_.transformInto[marketData.domain.Candlestick])

  private def orderbookStream(feedName: OrderbookFeed): Stream[F, Orderbook] = client
    .level2Messages(feedName)
    .collect { case m: Level2Message.Relevant => m }
    .map(_.events)
    .flatMap(Stream.emits)
    .pull.uncons1.flatMap {
      case Some((firstEvent, subsequentEvents)) =>
        val updatesTail = subsequentEvents.evalTapChunk { event =>
          Async[F].raiseWhen(event.`type` != Level2Message.Relevant.Event.Type.update)(
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

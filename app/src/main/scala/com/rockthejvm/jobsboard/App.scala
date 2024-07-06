package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*

import tyrian.CSS.*
import tyrian.*
import tyrian.Html.*

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import org.http4s
import org.http4s.client.websocket
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import _root_.io.circe
import monocle.syntax.all.*
import fs2.Stream
import concurrent.duration.DurationInt
import _root_.io.bullet.borer.Cbor
import _root_.io.bullet.borer.compat.scodec.*
import marketData.domain.Orderbook
import org.http4s.client.websocket.WSFrame
import org.http4s.QueryParamEncoder
import marketData.names.FeedName
import marketData.names.Currency
import com.rockthejvm.jobsboard.components.OrderbookView
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectExchange
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectFeed
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectCurrency1
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectCurrency2
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.TotalSelection
import _root_.io.bullet.borer
import marketData.names.FeedName.OrderbookFeed
import names.ExchangeName
import org.http4s.circe.CirceEntityCodec.*
import marketData.names.TradePair
import marketData.names.FeedName.Candlesticks
import marketData.domain.Candlestick
import com.rockthejvm.jobsboard.components.CandlestickView
import org.http4s.Request
import org.http4s.Method
import org.http4s.Header

object App {
  type Msg = NoOperation.type | Orderbook | Candlestick | MarketFeedSelectionStage | Sub[IO, ?] | InitTradePairs

  object NoOperation

  case class InitTradePairs(tradePairs: Map[ExchangeName, Map[Currency, Set[Currency]]])

  case class Model(
      selection: MarketFeedSelectionStage,
      subscriptionDef: Sub[IO, Msg],
      orderbook: Option[Orderbook],
      candlestick: Option[Candlestick]
  )
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg = Routing.none(NoOperation)

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {

    val client = http4s.dom.FetchClientBuilder[IO].create

    def allPairs(exchange: ExchangeName): IO[Map[Currency, Set[Currency]]] = {
      val allPairsReq = Request[IO](uri = http4s
        .Uri.fromString(
          s"https://typelevel-server-new.kotopoulion.xyz/${exchange.toString}/activeCurrencyPairs"
          // s"http://localhost:4041/${exchange.toString}/activeCurrencyPairs"
        )
        .getOrElse(None.get))

      client
        .expect[List[TradePair]](
          allPairsReq
        ).flatTap(IO.println).map { _.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap }
        .onError(err => IO.println(err.toString()))
    }

    val allPairsPerExchange: IO[Map[ExchangeName, Map[Currency, Set[Currency]]]] = ExchangeName
      .values.toList.map { exchange => allPairs(exchange).map(exchange -> _) }
      .sequence
      .map(_.toMap)

    Model(
      selection = MarketFeedSelectionStage.SelectExchange(
        // tradePairs = Map(names.Exchange.Binance -> Map(Currency("ETH") -> Set(Currency("BTC"), Currency("USDT"))))
        tradePairs = Map.empty
      ),
      Sub.None,
      orderbook = None,
      candlestick = None
    ) -> Cmd.Run(allPairsPerExchange.map(InitTradePairs.apply))
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = model.subscriptionDef

  override def view(model: Model): Html[Msg] =
    div(
      List(model.selection.view) ++
        model.orderbook.map(OrderbookView.view) ++
        model.candlestick.map(CandlestickView.view)
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (newModel, nextMsg) = msg match
      case NoOperation => model -> Cmd.None

      case InitTradePairs(tradePairs) =>
        model
          .focus(_.selection).replace(
            MarketFeedSelectionStage.SelectExchange(
              tradePairs = tradePairs
            )
          ) -> Cmd.None

      // TODO replace Msg -> Orderbook | etc (define type alias for union)
      case subDef: Sub[IO, Msg] =>
        model.copy(subscriptionDef = subDef) -> Cmd.None

      case ob: Orderbook => model.focus(_.orderbook).replace(Some(ob)) -> Cmd.None

      case cs: Candlestick => model.focus(_.candlestick).replace(Some(cs)) -> Cmd.None

      case selection: MarketFeedSelectionStage =>
        val sub: Sub[IO, Orderbook | Candlestick] = Option(selection)
          .collect { case ts: TotalSelection => ts.feedName -> ts.exchangeSelected }
          .map { case (feedName, exchange) =>
            feedName match {
              case feedName: OrderbookFeed => components.StreamFromServer.stream(exchange, feedName)
              case feedName: Candlesticks => components.StreamFromServer.stream(exchange, feedName)
            }
          }.getOrElse(Sub.None)

        model
          .focus(_.selection).replace(selection)
          .focus(_.orderbook).replace(None)
          -> Cmd.Emit(sub)

    newModel -> nextMsg
  }
}

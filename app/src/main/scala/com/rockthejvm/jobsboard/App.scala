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
import com.rockthejvm.Example
import _root_.io.circe
import monocle.syntax.all.*
import fs2.Stream
import concurrent.duration.DurationInt
import _root_.io.bullet.borer.Cbor
import _root_.io.bullet.borer.compat.scodec.*
import marketData.exchange.impl.binance.domain.Orderbook
import org.http4s.client.websocket.WSFrame
import org.http4s.QueryParamEncoder
import marketData.FeedDefinition
import marketData.Currency
import com.rockthejvm.jobsboard.components.OrderbookView
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.SelectExchange
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.SelectFeed
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.SelectCurrency1
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.SelectCurrency2
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage2.TotalSelection
import _root_.io.bullet.borer
import marketData.FeedDefinition.OrderbookFeed
import marketData.FeedDefinition.Stub
import names.Exchange
import org.http4s.circe.CirceEntityCodec.*

object App {
  type Msg = NoOperation.type | Orderbook | MarketFeedSelectionStage2 | Sub[IO, ?] | InitTradePairs

  object NoOperation

  case class InitTradePairs(tradePairs: Map[Exchange, Map[Currency, Set[Currency]]])

  case class Model(
      selection: MarketFeedSelectionStage2,
      subscriptionDef: Sub[IO, Msg],
      orderbook: Option[Orderbook]
  )
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg = Routing.none(NoOperation)

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {

    val stubFD: FeedDefinition[?] = FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC"))

    val client = http4s.dom.FetchClientBuilder[IO].create

    def allPairs(exchange: Exchange): IO[Map[Currency, Set[Currency]]] = client
      .expect[List[(Currency, Currency)]](
        uri = http4s
          .Uri.fromString(s"http://127.0.0.1:8080/${exchange.toString}/allCurrencyPairs")
          .getOrElse(None.get)
      ).map { _.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap }

    val allPairsPerExchange: IO[Map[Exchange, Map[Currency, Set[Currency]]]] = Exchange
      .values.toList.map { exchange => allPairs(exchange).map(exchange -> _) }
      .sequence
      .map(_.toMap)

    Model(
      selection = MarketFeedSelectionStage2.SelectExchange(
        // tradePairs = Map(names.Exchange.Binance -> Map(Currency("ETH") -> Set(Currency("BTC"), Currency("USDT"))))
        tradePairs = Map.empty
      ),
      Sub.None,
      orderbook = None
    ) -> Cmd.Run(allPairsPerExchange.map(InitTradePairs.apply))
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = model.subscriptionDef

  override def view(model: Model): Html[Msg] =
    div(
      List(model.selection.view) ++
        model.orderbook.map(OrderbookView.view)
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (newModel, nextMsg) = msg match
      case NoOperation => model -> Cmd.None

      case InitTradePairs(tradePairs) =>
        model
          .focus(_.selection).replace(
            MarketFeedSelectionStage2.SelectExchange(
              tradePairs = tradePairs
            )
          ) -> Cmd.None

      // TODO replace Msg -> Orderbook | etc (define type alias for union)
      case subDef: Sub[IO, Msg] =>
        model.copy(subscriptionDef = subDef) -> Cmd.None

      case ob: Orderbook => model.focus(_.orderbook).replace(Some(ob)) -> Cmd.None

      case selection: MarketFeedSelectionStage2 =>
        val sub: Sub[IO, Orderbook] = Option(selection)
          .collect { case ts: TotalSelection => ts.feedName -> ts.exchangeSelected }
          .map { case (feedName, exchange) =>
            feedName match {
              case feedName: OrderbookFeed => components.StreamFromServer.stream(exchange, feedName)
              case Stub(_) => Sub.None
            }
          }.getOrElse(Sub.None)

        model
          .focus(_.selection).replace(selection)
          .focus(_.orderbook).replace(None)
          -> Cmd.Emit(sub)

    newModel -> nextMsg
  }
}

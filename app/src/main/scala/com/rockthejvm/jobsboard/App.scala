package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*

import tyrian.CSS.*
import tyrian.*
import tyrian.Html.*

import cats.effect.*
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

object App {
  type Msg = NoOperation.type | Displayable | Orderbook | MarketFeedSelectionStage2 | Sub[IO, ?]

  object NoOperation

  case class Model(
      selection: MarketFeedSelectionStage2,
      subscriptionDef: Sub[IO, Msg],
      sells: List[(BigDecimal, BigDecimal)]
  )

  case class UpdateSells(sells: List[(BigDecimal, BigDecimal)])

  case class Displayable(value: Orderbook | FeedDefinition.Stub.Message)
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg = Routing.none(NoOperation)

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {

    val stubFD: FeedDefinition[?] = FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC"))

    // val streamFromServer = {
    //   http4s
    //     .dom.WebSocketClient[IO].connectHighLevel(
    //       websocket.WSRequest(
    //         uri = http4s
    //           .Uri.fromString("ws://127.0.0.1:8080")
    //           .map(_.withQueryParam("feedName", stubFD))
    //           .getOrElse(None.get)
    //       )
    //     ).allocated
    //     .map { case (conn, cleanup) =>
    //       val receiveStreamTransformed: Stream[IO, UpdateSells] =
    //         Stream.repeatEval(conn.send(WSFrame.Text("")) <* IO.sleep(500.millis)) `zipRight`
    //           conn
    //             .receiveStream
    //             .map {
    //               case Binary(data, _) => Cbor.decode(data).to[Orderbook].value
    //               case _ => throw new Exception("unexpected non binary ws frame")
    //             }.map(_.askLevelToQuantity.toList)
    //             .map(UpdateSells.apply)

    //       Model.SubscriptionDef("server stream", receiveStreamTransformed, cleanup)
    //     }
    // }

    Model(
      selection = MarketFeedSelectionStage2.SelectExchange(
        alreadySelected = None,
        tradePairs = Map(names.Exchange.Binance -> Map(Currency("ETH") -> Set(Currency("BTC"), Currency("USD"))))
      ),
      Sub.None,
      List((1168.49, 0.0), (1164.69, 12.0211), (1163.38, 33.0049))
    ) -> Cmd.None
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = {
    // val subDefs = model
    //   .subscriptionDefs.map { subDef =>
    //     Sub.make(subDef.name)(subDef.stream)(subDef.cleanup)
    //   }

    // val pf: PartialFunction[MarketFeedSelectionStage2, (names.Exchange, FeedDefinition[?])] = {
    //   case MarketFeedSelectionStage2.TotalSelection(_, exchange, feedName) => (exchange, feedName)
    // }

    // pf.lift(model.selection)

    model.subscriptionDef
  }

  override def view(model: Model): Html[Msg] =
    div(
      model.selection.view,
      OrderbookView.view(model)
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (newModel, nextMsg) = msg match
      case NoOperation => model -> Cmd.None

      // TODO replace Msg -> Orderbook | etc (define type alias for union)
      case subDef: Sub[IO, Msg] =>
        model.copy(subscriptionDef = subDef) -> Cmd.None

      // case UpdateSells(sellsNew) =>
      //   model.focus(_.sells).replace(sellsNew) -> Cmd.None

      // case Displayable(displayable) =>
      //   displayable match {
      //     case ob: Orderbook => model.focus(_.sells).replace(ob.askLevelToQuantity.toList) -> Cmd.None
      //     case _ => throw Exception("shouldve been Orderbook")
      //   }

      case ob: Orderbook => model.focus(_.sells).replace(ob.askLevelToQuantity.toList) -> Cmd.None

      case selection: MarketFeedSelectionStage2 =>
        val cmd: Sub[IO, Orderbook] = Option(selection)
          .collect { case ts: TotalSelection => ts.feedName }
          .map {
            case obf: OrderbookFeed => components.StreamFromServer.stream(obf)
            case Stub(_value) => Sub.None
          }.getOrElse(Sub.None)

        // val cmd = selection match {
        //   case selection: TotalSelection =>
        //     Cmd.Run(
        //       components
        //         .StreamFromServer.stream(selection.feedName)
        //     )
        //   case _ => Cmd.None
        // }

        model.focus(_.selection).replace(selection) -> Cmd.Emit(cmd)

    newModel -> nextMsg
  }
}

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
  type Msg = NoOperation.type | Orderbook | MarketFeedSelectionStage2 | Sub[IO, ?]

  object NoOperation

  case class Model(
      selection: MarketFeedSelectionStage2,
      subscriptionDef: Sub[IO, Msg],
      sells: List[(BigDecimal, BigDecimal)],
      orderbook: Option[Orderbook]
  )

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
      List((1168.49, 0.0), (1164.69, 12.0211), (1163.38, 33.0049)),
      orderbook = None
    ) -> Cmd.None
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

      // TODO replace Msg -> Orderbook | etc (define type alias for union)
      case subDef: Sub[IO, Msg] =>
        model.copy(subscriptionDef = subDef) -> Cmd.None

      case ob: Orderbook => model.focus(_.orderbook).replace(Some(ob)) -> Cmd.None

      case selection: MarketFeedSelectionStage2 =>
        val sub: Sub[IO, Orderbook] = Option(selection)
          .collect { case ts: TotalSelection => ts.feedName }
          .map {
            case obf: OrderbookFeed => components.StreamFromServer.stream(obf)
            case Stub(_value) => Sub.None
          }.getOrElse(Sub.None)

        model
          .focus(_.selection).replace(selection)
          .focus(_.orderbook).replace(None)
          -> Cmd.Emit(sub)

    newModel -> nextMsg
  }
}

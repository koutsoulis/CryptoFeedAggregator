package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*
import tyrian.*

import tyrian.CSS.*
import tyrian.*
import tyrian.Html.*

import cats.effect.*
import core.PageManager
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

object App {
  type Msg = NoOperation.type | Model.SubscriptionDef | UpdateSells

  object NoOperation

  case class Model(
      displayStatus: String,
      subscriptionDefs: List[Model.SubscriptionDef],
      sells: List[(BigDecimal, BigDecimal)]
  )

  case class UpdateSells(sells: List[(BigDecimal, BigDecimal)])

  object Model {
    case class SubscriptionDef(name: String, stream: fs2.Stream[IO, Msg], cleanup: IO[Unit])
  }
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg = Routing.none(NoOperation)

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {

    val stubFD: FeedDefinition[?] = FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC"))

    val streamFromServer = {
      http4s
        .dom.WebSocketClient[IO].connectHighLevel(
          websocket.WSRequest(
            uri = http4s
              .Uri.fromString("ws://127.0.0.1:8080")
              .map(_.withQueryParam("feedName", stubFD))
              .getOrElse(None.get)
          )
        ).allocated
        .map { case (conn, cleanup) =>
          val receiveStreamTransformed: Stream[IO, UpdateSells] =
            Stream.repeatEval(conn.send(WSFrame.Text("")) <* IO.sleep(500.millis)) `zipRight`
              conn
                .receiveStream
                .map {
                  case Binary(data, _) => Cbor.decode(data).to[Orderbook].value
                  case _ => throw new Exception("unexpected non binary ws frame")
                }.map(_.askLevelToQuantity.toList)
                .map(UpdateSells.apply)

          Model.SubscriptionDef("server stream", receiveStreamTransformed, cleanup)
        }
    }

    Model(
      "",
      List.empty,
      List((1168.49, 0.0), (1164.69, 12.0211), (1163.38, 33.0049))
    ) -> Cmd.Run(streamFromServer)
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = {
    val subDefs = model
      .subscriptionDefs.map { subDef =>
        Sub.make(subDef.name)(subDef.stream)(subDef.cleanup)
      }

    Sub.combineAll(subDefs)
  }

  override def view(model: Model): Html[Msg] =
    div {
      val maxVolume = model.sells.map(_._2).max

      def outerRow(elems: List[tyrian.Elem[Msg]]): tyrian.Html[Msg] = div(
        style(
          CSS.height("30px") |+|
            CSS.width("100%") |+|
            CSS.position("relative")
        )
      )(elems)

      def row(elems: List[tyrian.Elem[Msg]]): tyrian.Html[Msg] = div(
        style(
          CSS.display("flex") |+|
            CSS.position("absolute") |+|
            CSS.width("100%") |+|
            CSS.top("0")
        )
      )(elems)

      def cell(text: String): tyrian.Html[Msg] = div(
        style(
          CSS.`flex-grow`("1") |+|
            CSS.display("flex") |+|
            CSS.`justify-content`("center") |+|
            CSS.`align-items`("center") |+|
            CSS.color("black")
        )
      )(text)

      def percentageBar(width: BigDecimal): tyrian.Html[Msg] = div(
        style(
          CSS.`background-color`("green") |+|
            CSS.height("100%") |+|
            CSS.position("absolute") |+|
            CSS.right("0") |+|
            CSS.width(s"$width%")
        )
      )("")

      val rows: List[tyrian.Html[Msg]] = model.sells.map { case (price, volume) =>
        outerRow(
          List(
            percentageBar(volume * 100 / maxVolume),
            row(
              List(
                cell(price.toString),
                cell(volume.toString)
              )
            )
          )
        )
      }
      div(style(CSS.display("flex")))(
        div(style(CSS.flex("1")))(
          children = rows: List[tyrian.Html[Msg]]
        ),
        div(style(CSS.flex("1")))(
          children = rows: List[tyrian.Html[Msg]]
        )
      )
    }

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (newModel, nextMsg) = msg match
      case NoOperation => model -> Cmd.None

      case subDef: Model.SubscriptionDef =>
        model.copy(subscriptionDefs = model.subscriptionDefs.prepended(subDef)) -> Cmd.None

      case UpdateSells(sellsNew) =>
        model.focus(_.sells).replace(sellsNew) -> Cmd.None

    newModel -> nextMsg
  }
}

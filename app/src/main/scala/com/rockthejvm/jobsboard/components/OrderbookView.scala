package com.rockthejvm.jobsboard.components

import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*
import marketData.exchange.impl.binance.domain.Orderbook

object OrderbookView {
  def view(ob: Orderbook): Html[Msg] =
    div {
      val asks = ob.askLevelToQuantity.toList
      val bids = ob.bidLevelToQuantity.toList
      val maxAsksVolume = asks.map(_._2).max
      val maxBidsVolume = bids.map(_._2).max

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

      def percentageBar(width: BigDecimal, color: "green" | "red"): tyrian.Html[Msg] = div(
        style(
          CSS.`background-color`("green") |+|
            CSS.height("100%") |+|
            CSS.position("absolute") |+|
            CSS.right("0") |+|
            CSS.width(s"$width%")
        )
      )("")

      val bidRows: List[tyrian.Html[Msg]] = bids.map { case (price, volume) =>
        outerRow(
          List(
            percentageBar(width = volume * 100 / maxBidsVolume, color = "green"),
            row(
              List(
                cell(price.toString),
                cell(volume.toString)
              )
            )
          )
        )
      }

      val askRows = asks.map { case (price, volume) =>
        outerRow(
          List(
            percentageBar(width = volume * 100 / maxAsksVolume, color = "red"),
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
          children = bidRows: List[tyrian.Html[Msg]]
        ),
        div(style(CSS.flex("1")))(
          children = askRows: List[tyrian.Html[Msg]]
        )
      )
    }

}

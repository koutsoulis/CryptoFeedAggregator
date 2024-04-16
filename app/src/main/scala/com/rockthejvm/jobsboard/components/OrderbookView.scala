package com.rockthejvm.jobsboard.components

import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*

object OrderbookView {
  def view(model: Model): Html[Msg] =
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

}

package com.rockthejvm.jobsboard.components

import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*
import marketData.domain.Candlestick

object CandlestickView {
  def view(candlestick: Candlestick): Html[Msg] = {
    div()(
      h2()("Candlestick Data"),
      ul()(
        li()("Open: " + candlestick.open.toString),
        li()("Close: " + candlestick.close.toString),
        li()("High: " + candlestick.high.toString),
        li()("Low: " + candlestick.low.toString)
      )
    )
  }
}

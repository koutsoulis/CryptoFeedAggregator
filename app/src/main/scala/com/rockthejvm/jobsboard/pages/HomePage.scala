package com.rockthejvm.jobsboard.pages

import tyrian.Html
import tyrian.*
import tyrian.Html.*
import com.rockthejvm.jobsboard.App.Msg
import com.rockthejvm.jobsboard.App.Model

object HomePage extends Page {
  override def view(model: Model): Html[Msg] =
    div("Home Page - TODO")

}

package com.rockthejvm.jobsboard.pages

import tyrian.*
// import scala.util.Try
// import com.rockthejvm.jobsboard.pages.Page.NavigateTo
// import tyrian.cmds.Logger
import com.rockthejvm.jobsboard.App.Msg
import com.rockthejvm.jobsboard.App.Model

trait Page {
  def view(model: Model): Html[Msg]
}

object Page {
  type SubtypesExhaustive = HomePage | JobPage.type | NotFoundPage.type | StreamingExamplePage.type
}

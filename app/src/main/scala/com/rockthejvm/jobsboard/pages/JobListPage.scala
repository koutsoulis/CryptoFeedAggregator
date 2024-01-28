package com.rockthejvm.jobsboard.pages

import cats.effect.*
import tyrian.Html
import com.rockthejvm.jobsboard.pages.Page.Msg
import tyrian.Cmd
import tyrian.*
import tyrian.Html.*

object JobListPage extends Page {

  override def initCmd: Cmd[IO, Msg] = Cmd.None

  // override def update(msg: Msg): (Page, Cmd[IO, Msg]) = this -> Cmd.None

  override def view: Html[Msg] =
    div("Job List Page - TODO")

}

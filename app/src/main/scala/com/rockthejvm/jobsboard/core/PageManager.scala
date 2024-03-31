package com.rockthejvm.jobsboard.core

import com.rockthejvm.jobsboard.pages.Page
import com.rockthejvm.jobsboard.pages.HomePage
import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*
import cats.effect.*

case class PageManager private (
    current: Page
) {
  def update(msg: PageManager.Msg): (PageManager, Cmd[IO, Msg]) = msg match {
    case PageManager.NavigateTo(page) =>
      val action = page match {
        case _ => Cmd.None
      }
      this.copy(current = page) -> action

    // case _ => this
  }

  def view(model: Model): Html[Msg] = current.view(model)
}

object PageManager {
  type Msg = NavigateTo
  final case class NavigateTo(page: Page.SubtypesExhaustive)

  def apply: PageManager = {
    val homePage = new HomePage
    new PageManager(current = homePage)
  }
}

// page,
// Cmd
//   .SideEffect(
//     FetchClientBuilder[IO]
//       .create.statusFromString(s"http://localhost:4041/simple")
//   ))

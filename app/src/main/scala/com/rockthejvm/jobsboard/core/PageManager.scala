package com.rockthejvm.jobsboard.core

import com.rockthejvm.jobsboard.pages.Page
import com.rockthejvm.jobsboard.pages.HomePage
import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import tyrian.*
import tyrian.Html.*

case class PageManager private (
    current: Page
) {
  def update(msg: PageManager.Msg): PageManager = msg match {
    case PageManager.NavigateTo(page) =>
      this.copy(current = page)

    // case _ => this
  }

  def view(model: Model): Html[Msg] = current.view(model)
}

object PageManager {
  type Msg = NavigateTo
  final case class NavigateTo(page: Page.SubtypesExhaustive)

  def apply: PageManager = {
    val homePage = HomePage
    new PageManager(current = homePage)
  }
}

// page,
// Cmd
//   .SideEffect(
//     FetchClientBuilder[IO]
//       .create.statusFromString(s"http://localhost:4041/simple")
//   ))

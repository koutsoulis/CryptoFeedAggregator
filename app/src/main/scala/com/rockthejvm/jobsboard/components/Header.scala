package com.rockthejvm.jobsboard.components

import tyrian.*
import tyrian.Html.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import com.rockthejvm.jobsboard.pages
import com.rockthejvm.jobsboard.pages.Page
import com.rockthejvm.jobsboard.core.PageManager
import com.rockthejvm.jobsboard.pages.HomePage

object Header {
  def view =
    div(`class` := "header-container")(
      renderLogo,
      div(`class` := "header-nav")(
        ul(`class` := "header-links")(
          renderNavlink("home", new pages.HomePage),
          renderNavlink("streaming", pages.StreamingExamplePage)
        )
      )
    )

  @js.native
  @JSImport("/static/img/fiery-lava 128x128.png", JSImport.Default)
  private val logoImage: String = js.native

  private def renderLogo =
    div(
      img(
        `class` := "logo-image",
        src := logoImage,
        alt := "Rock the JVM"
      )
    )

  private def renderNavlink(text: String, page: Page.SubtypesExhaustive) =
    li(`class` := "nav-item")(
      button(
        onEvent(
          "click",
          e => {
            PageManager.NavigateTo(page)
          }
        )
      )(text)
    )
}

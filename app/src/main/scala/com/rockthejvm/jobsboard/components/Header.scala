package com.rockthejvm.jobsboard.components

import tyrian.*
import tyrian.Html.*
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import com.rockthejvm.jobsboard.core.Router

object Header {
  def view =
    div(`class` := "header-container")(
      renderLogo,
      div(`class` := "header-nav")(
        ul(`class` := "header-links")(
          renderNavlink("job", "/job"),
          renderNavlink("login", "/login"),
          renderNavlink("signup", "/signup")
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

  private def renderNavlink(text: String, location: String) =
    li(`class` := "nav-item")(
      a(
        href := location,
        onEvent(
          "click",
          e => {
            e.preventDefault()
            Router.Msg.ChangeLocation(location)
          }
        )
      )(text)
    )
}

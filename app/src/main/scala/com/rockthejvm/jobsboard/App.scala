package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*
import org.scalajs.dom.window
import tyrian.*
import tyrian.Html.*
import cats.effect.IO
import com.rockthejvm.jobsboard.core.Router
import com.rockthejvm.jobsboard.core.Router.Msg

object App {
  case class Model(router: Router)
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianApp[Router.Msg, Model] {

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {
    val (router, cmd) = Router.startAt(window.location.pathname)

    Model(router) -> cmd
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.make(
      "urlChange",
      model
        .router.history.state.discrete.map(_.get)
        .map(loc => Router.Msg.ChangeLocation.apply(loc, true))
    )

  override def view(model: Model): Html[Msg] =
    div(
      components.Header.view,
      div(s"you are now at: ${model.router.location}")
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (router, cmd) = model.router.update(msg)

    Model(router) -> cmd
  }

  // def doSomething(containerId: String) =
  //   document.getElementById(containerId).innerHTML = "SCALA ROCKS"
}

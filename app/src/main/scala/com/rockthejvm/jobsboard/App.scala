package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*
// import org.scalajs.dom.window
import tyrian.*
import tyrian.Html.*
import cats.effect.IO
// import com.rockthejvm.jobsboard.core.Router
// import com.rockthejvm.jobsboard.pages.Page.NavigateTo
import core.PageManager

object App {
  type Msg = NoOperation.type | PageManager.NavigateTo

  case object NoOperation

  case class Model(pageManager: PageManager) {
    // def page: pages.Page = pages.Page.get(router.location)
  }
}

import App.*

@JSExportTopLevel("RockTheJvmApp")
class App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg = Routing.none(NoOperation)

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) = {
    // val urlLocation = window.location.pathname
    // val (router, routerCmd) = Router.startAt(urlLocation)
    // val pageUrl = Page.Url.of(urlLocation)

    Model(PageManager.apply) -> Cmd.None
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = Sub.None
  // Sub.make(
  //   "urlChange",
  //   model
  //     .router.history.state.discrete.map(_.get)
  //     .map(loc => Msg.ChangeLocation.apply(loc, true))
  // )

  override def view(model: Model): Html[Msg] =
    div(
      components.Header.view,
      model.pageManager.view(model)
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = { msg =>
    val (newModel, nextMsg) = msg match
      case msg: PageManager.NavigateTo =>
        model.copy(pageManager = model.pageManager.update(msg)) -> NoOperation
      case NoOperation => model -> NoOperation

    newModel -> Cmd.None
  }

  // def doSomething(containerId: String) =
  //   document.getElementById(containerId).innerHTML = "SCALA ROCKS"
}

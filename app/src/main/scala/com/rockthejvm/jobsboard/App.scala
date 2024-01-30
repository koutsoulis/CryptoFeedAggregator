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
  type Msg = NoOperation.type | PageManager.NavigateTo | UpdatedStatus

  object NoOperation

  case class UpdatedStatus(value: Int)

  case class Model(pageManager: PageManager, displayStatus: Int) {
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

    Model(PageManager.apply, 0) -> Cmd.None
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.make(
      "myStream",
      stream = fs2
        .Stream.fromIterator[IO].apply(
          iterator = Iterator.iterate(0)(_ + 1).map(UpdatedStatus.apply),
          chunkSize = 1
        )
    )
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
        model.copy(pageManager = model.pageManager.update(msg)) -> Cmd.None

      case NoOperation => model -> Cmd.None

      case UpdatedStatus(value) => model.copy(displayStatus = value) -> Cmd.None

    newModel -> nextMsg
  }

  // def doSomething(containerId: String) =
  //   document.getElementById(containerId).innerHTML = "SCALA ROCKS"
}

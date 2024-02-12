package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*
// import org.scalajs.dom.window
import tyrian.*
import tyrian.Html.*
import tyrian.syntax.*
import cats.effect.*
// import com.rockthejvm.jobsboard.core.Router
// import com.rockthejvm.jobsboard.pages.Page.NavigateTo
import core.PageManager
import org.http4s
import org.http4s.client.websocket
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import com.rockthejvm.Example
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import monocle.syntax.all.*

object App {
  type Msg = NoOperation.type | PageManager.NavigateTo | UpdatedStatus | Model.SubscriptionDef | WSDF

  object NoOperation

  case class WSDF(value: WSDataFrame)

  case class UpdatedStatus(value: String)

  case class Model(pageManager: PageManager, displayStatus: String, subscriptionDefs: List[Model.SubscriptionDef])

  object Model {
    case class SubscriptionDef(name: String, stream: fs2.Stream[IO, Msg], cleanup: IO[Unit])
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

    val streamFromServer = {
      http4s
        .dom.WebSocketClient[IO].connectHighLevel(
          websocket.WSRequest(
            uri =
              http4s.Uri.fromString("wss://typelevel-project-backend.kotopoulion.xyz:4041/simpleWS").getOrElse(None.get)
          )
        ).allocated.map { case (conn, cleanup) => conn.receiveStream -> cleanup }
        .map { case (conn, cleanup) =>
          Model.SubscriptionDef("server stream", conn.map(WSDF.apply), cleanup)
        }
      // .stream(
      //   http4s.Request(
      //     uri = http4s.Uri.fromString("http://localhost:4041/simpleWS").getOrElse(None.get)
      //   )
      // ).map(_.bodyText).flatten
    }

    Model(PageManager.apply, "", List.empty) -> Cmd.Run(streamFromServer)
  }

  override def subscriptions(model: Model): Sub[IO, Msg] = {
    val subDefs = model
      .subscriptionDefs.map { subDef =>
        Sub.make(subDef.name)(subDef.stream)(subDef.cleanup)
      }

    Sub.combineAll(subDefs)
  }
  // Sub.make(
  //   "myStream",
  //   stream =
  //   // fs2
  //   // .Stream.fromIterator[IO].apply(
  //   //   iterator = Iterator.iterate(0)(_ + 1).map(UpdatedStatus.apply),
  //   //   chunkSize = 1
  //   // )
  // )
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
        model.focus(_.pageManager).modify(_.update(msg)) -> Cmd.None

      case NoOperation => model -> Cmd.None

      case UpdatedStatus(value) => model.focus(_.displayStatus).replace(value) -> Cmd.None

      case subDef: Model.SubscriptionDef =>
        model.focus(_.subscriptionDefs).modify(_.prepended(subDef)) -> Cmd.None

      case WSDF(wsDataFrame) =>
        wsDataFrame match {
          case Text(data, last) =>
            model
              .focus(_.displayStatus).replace(
                circe.parser.decode[Example.WrappedString](data).map(_.value).getOrElse(s"decoding $data failed")
              ) -> Cmd.None

          case _ => throw new Exception("impossible")
        }

    newModel -> nextMsg
  }

  // def doSomething(containerId: String) =
  //   document.getElementById(containerId).innerHTML = "SCALA ROCKS"
}

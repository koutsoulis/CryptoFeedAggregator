package com.rockthejvm.jobsboard

import scala.scalajs.js.annotation.*
// import org.scalajs.dom.window
import tyrian.*

// import tyrian.Html.*
import tyrian.Html.div
// import tyrian.syntax.*

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
// import _root_.io.circe.generic.semiauto.*
// import monocle.syntax.all.*
import fs2.Stream
import concurrent.duration.DurationInt

object App {
  type Msg = NoOperation.type | PageManager.NavigateTo | UpdatedStatus | Model.SubscriptionDef | WSDF | UpdateSells

  object NoOperation

  case class WSDF(value: WSDataFrame)

  case class UpdatedStatus(value: String)

  case class Model(
      pageManager: PageManager,
      displayStatus: String,
      subscriptionDefs: List[Model.SubscriptionDef],
      sells: List[(Double, Double)]
  )

  case class UpdateSells(sells: List[(Double, Double)])

  object Model {
    case class SubscriptionDef(name: String, stream: fs2.Stream[IO, Msg], cleanup: IO[Unit])
  }
}

object Stubs {
  val obStream: Stream[IO, List[(Double, Double)]] =
    Stream
      .bracket {
        val iterator = Iterator.iterate(
          List(
            (1168.49, 0.0),
            (1164.69, 12.0211),
            (1163.38, 33.0049),
            (1160.98, 45.5622),
            (1158.64, 60.4819),
            (1154.04, 71.5594),
            (1146.54, 83.2051),
            (1133.37, 106.8834),
            (1129.63, 127.1219),
            (1126.89, 145.2484),
            (1115.14, 155.8074),
            (1113.54, 171.8438),
            (1110.49, 184.443),
            (1106.92, 202.3068),
            (1106.7, 224.5185),
            (1104.03, 244.5286),
            (1101.99, 256.5801),
            (1099.57, 272.8992),
            (1099.47, 289.2549),
            (1095.76, 300.107),
            (1091.75, 320.0837),
            (1091.37, 334.7523),
            (1086.9, 357.9836),
            (1086.6, 375.3844),
            (1081.13, 387.3668),
            (1079.3, 403.3796),
            (1074.56, 420.0898),
            (1069.69, 438.8176),
            (1068.59, 462.0495),
            (1056.35, 484.2044),
            (1052.93, 507.0559),
            (1052.03, 529.1966),
            (1047.46, 541.6345),
            (1033.06, 551.942),
            (1030.42, 569.7072),
            (1025.65, 583.7136),
            (1023.38, 608.1764),
            (1020.04, 620.0944),
            (1018.53, 644.661),
            (1014.92, 661.6777)
          )
        ) { list =>
          val factor = scala.util.Random.nextFloat()
          list
          // .map { case (p1, p2) => (factor * p1) -> p2 }
        }
        val str = Stream
          .fromIterator[IO](
            iterator,
            1
          )

        IO.println("openning stream") *> IO.delay(str)
      }(_ => IO.println("closing stream")).flatten.metered(3.seconds).evalTap(IO.println)
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
            uri = http4s.Uri.fromString("wss://typelevel-project-backend.kotopoulion.xyz:4041/simpleWS").getOrElse(None.get)
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

    // Model(PageManager.apply, "", List.empty) -> Cmd.Run(streamFromServer)
    Model(
      PageManager.apply,
      "",
      List(
        Model.SubscriptionDef("asd", Stubs.obStream.map(UpdateSells.apply), IO.unit)
      ),
      // List.empty
      List((1168.49, 0.0), (1164.69, 12.0211), (1163.38, 33.0049))
    ) -> Cmd.None
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
        val (newPM, action) = model.pageManager.update(msg)
        model.copy(pageManager = newPM) -> action

      case NoOperation => model -> Cmd.None

      case UpdatedStatus(value) =>
        model.copy(displayStatus = value) -> Cmd.None
      // model.focus(_.displayStatus).replace(value) -> Cmd.None

      case subDef: Model.SubscriptionDef =>
        model.copy(subscriptionDefs = model.subscriptionDefs.prepended(subDef)) -> Cmd.None

      case WSDF(wsDataFrame) =>
        wsDataFrame match {
          case Text(data, last) =>
            model
              .copy(displayStatus =
                circe.parser.decode[Example.WrappedString](data).map(_.value).getOrElse(s"decoding $data failed")) -> Cmd.None

          case _ => throw new Exception("impossible")
        }

      case UpdateSells(sellsNew) =>
        model.copy(sells = sellsNew, pageManager = PageManager.apply) -> Cmd.None
      // model.focus(_.sells).replace(sellsNew) -> Cmd.None

    newModel -> nextMsg
  }
}

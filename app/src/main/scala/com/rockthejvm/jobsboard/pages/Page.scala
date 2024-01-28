package com.rockthejvm.jobsboard.pages

import cats.effect.*
import tyrian.*
import tyrian.Html.*
// import scala.util.Try
// import com.rockthejvm.jobsboard.pages.Page.NavigateTo
// import tyrian.cmds.Logger
import org.http4s.dom.FetchClientBuilder

object Page {
  sealed trait Msg
  final case class NavigateTo(page: HomePage.type | JobListPage.type) extends Msg

  // def get(location: String): Page = {
  //   case "/jobs"
  // }

  enum Url(val value: String) {
    case NotFound extends Url("/notFound")
    case Jobs extends Url("/jobs")
    case Home extends Url("/")
    case Empty extends Url("")
  }

  object Url {
    private val valueToUrl: Map[String, Url] = Url.values.map(url => url.value -> url).toMap

    def of(s: String): Url = valueToUrl(s)
  }

  // val of: Url => Page = {
  //   case Url.NotFound => new NotFoundPage
  //   case Url.Jobs => new JobPage
  //   // case Url.JobPage => new JobPage
  //   case Url.Home => ???
  //   case Url.Empty => ???
  // }

  def update(msg: Msg): (Page, Cmd[IO, Msg]) = msg match {
    case NavigateTo(page) =>
      // page -> Logger.consoleLog[IO]("LOG THIS THING")
      page ->
        Cmd
          .SideEffect(
            FetchClientBuilder[IO]
              .create.statusFromString(s"http://localhost:4041/simple")
          )
  }
}

trait Page {
  import Page.Msg

  def initCmd: Cmd[IO, Msg]

  def view: Html[Msg]
}

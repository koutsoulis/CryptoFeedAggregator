package com.rockthejvm.jobsboard.core

import tyrian.*
import cats.effect.*
import fs2.dom.History

case class Router private (location: String, history: History[IO, String]) {
  import Router.*

  def update(msg: Msg): (Router, Cmd[IO, Msg]) = msg match
    case Msg.ChangeLocation(newLocation, browserTriggered) =>
      if (newLocation == this.location) {
        this -> Cmd.None
      } else {
        this.copy(location = newLocation) -> {
          if (!browserTriggered) goto(newLocation, history) else Cmd.None
        }
      }
    // case ExternalRedirect(location) => this.copy(location = location)
    case _ => throw new Exception("unimplemented")
}

object Router {
  sealed trait Msg
  object Msg {
    final case class ChangeLocation(
        location: String,
        browserTriggered: Boolean = false
    ) extends Msg
    final case class ExternalRedirect(location: String) extends Msg
  }

  def startAt(location: String): (Router, Cmd[IO, Msg]) =
    val history = History[IO, String]
    Router(location, history) -> goto(location, history)

  def goto(location: String, history: History[IO, String]): Cmd[IO, Msg] =
    Cmd.SideEffect(history.pushState(location, location))
}

package com.rockthejvm.jobsboard.logging

import cats.MonadError
import cats.effect.IO
import cats.implicits.*
import org.typelevel.log4cats.Logger

object syntax {
  extension [F[_]: Logger, E, A](fa: F[A])(using me: MonadError[F, E]) {
    def log(successPrint: A => String, errorPrint: E => String): F[A] = ???
  }
}

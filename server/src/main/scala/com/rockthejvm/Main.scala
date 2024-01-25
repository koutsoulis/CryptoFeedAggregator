package com.rockthejvm

import cats.Monad
import cats.effect.*
import cats.effect.IO.{IOCont, Uncancelable}
import com.rockthejvm.jobsboard.playground.EmberConfig
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.*
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import scala.util.chaining.*
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]

    jobsboard
      .modules
      .Core[IO]
      .useForever
      .as(())
  }
}

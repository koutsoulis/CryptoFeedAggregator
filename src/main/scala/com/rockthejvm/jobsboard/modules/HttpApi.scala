package com.rockthejvm.jobsboard.modules

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import org.http4s
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.HttpRoutes
import com.rockthejvm.jobsboard.http.routes

class HttpApi[F[_]: Async] private (jobs: JobsDao[F]) {
  private val jobRoutes =
    routes.JobRoutes[F](jobs).endpoints

  val endpoints: HttpRoutes[F] = {
    jobRoutes
  }
}

object HttpApi {
  def apply[F[_]: Async](jobs: JobsDao[F]): HttpApi[F] = new HttpApi[F](jobs)
}

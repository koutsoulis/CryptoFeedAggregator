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
import com.rockthejvm.jobsboard.domain.job.JobInfo
import sttp.tapir.server.ServerEndpoint
import java.util.UUID
import com.rockthejvm.jobsboard.http.routes.JobRoutes

class HttpApi[F[_]: Async] private (jobs: JobsDao[F]) {
  private val jobRoutes =
    routes.JobRoutes[F](jobs).createJobRoute2

  val endpoints = {
    jobRoutes
  }
}

object HttpApi {
  def apply[F[_]: Async](jobs: JobsDao[F]): HttpApi[F] = new HttpApi[F](jobs)
}

package com.rockthejvm.jobsboard.modules

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import doobie.hikari.HikariTransactor
import org.http4s.server.Server
import org.typelevel.log4cats

class Core private (_emberServer: Server)

object Core {
  def apply[F[_]: Async: log4cats.LoggerFactory]: Resource[F, Core] = {
    val postgresResource: Resource[F, HikariTransactor[F]] = for {
      ec <- doobie.util.ExecutionContexts.fixedThreadPool(32)
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver",
        "jdbc:postgresql:board",
        "docker",
        "docker",
        ec
      )
    } yield xa

    // postgresResource
    //   .map(LiveJobsDao.apply)
    //   .map(HttpServerEndpoints.apply).product(Resource.pure(WebSockServerEndpoints()))
    //   .flatMap(EmberServer.apply)
    //   .map(new Core(_))

    Resource
      .pure(WebSockServerEndpoints())
      .flatMap(EmberServer.apply)
      .map(new Core(_))
  }

}

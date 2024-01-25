package com.rockthejvm.jobsboard.modules.doobieTestHelpers

import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.testcontainers.containers.PostgreSQLContainer

object ContainerPg {
  def transactorResource: Resource[IO, doobie.Transactor[IO]] =
    (
      doobie.ExecutionContexts.fixedThreadPool[IO](32),
      psqlContainerRs
    ).flatMapN { case (execCtx, container) =>
      doobie
        .hikari
        .HikariTransactor
        .newHikariTransactor[IO](
          driverClassName = container.getDriverClassName(),
          url = container.getJdbcUrl(),
          user = container.getUsername(),
          pass = container.getPassword(),
          connectEC = execCtx
        )
    }

  private def psqlContainerRs =
    Resource.fromAutoCloseable(
      IO.blocking {
        val container = PostgreSQLContainer("postgres")
        container.start()

        container
      }
    )
}

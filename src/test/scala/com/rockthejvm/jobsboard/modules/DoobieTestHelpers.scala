package com.rockthejvm.jobsboard.modules

import doobie.implicits.*
import doobie.postgres.implicits.*
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.testcontainers.containers.PostgreSQLContainer
import com.zaxxer.hikari.HikariConfig

object DoobieTestHelpers {
  def psqlContainerRs =
    Resource.fromAutoCloseable(
      IO.blocking {
        val container = PostgreSQLContainer("postgres")
        container.start()

        container
      }
    )

  def postgres: Resource[IO, doobie.Transactor[IO]] =
    (
      doobie.ExecutionContexts.fixedThreadPool[IO](1),
      psqlContainerRs
    ).flatMapN {
      case (execCtx, container) =>
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
}

package com.rockthejvm.jobsboard.modules.doobieTestHelpers

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import cats.effect.*
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.Config
import doobie.hikari.HikariTransactor

object EmbeddedPg {
  def transactorResource: Resource[IO, doobie.Transactor[IO]] = Resource
    .fromAutoCloseable {
      IO.interruptible(EmbeddedPostgres.start())
    }.evalMap { pg =>
      IO(pg.getJdbcUrl(username, dbName))
    }.map { jdbcUrl =>
      Config(jdbcUrl)
    }.flatMap(HikariTransactor.fromConfig(_))

  private def dbName =
    "postgres" // consistent with hardcoded values in io.zonky.test.db.postgres.embedded.EmbeddedPostgres
  private def username =
    "postgres" // consistent with hardcoded values in io.zonky.test.db.postgres.embedded.EmbeddedPostgres
}

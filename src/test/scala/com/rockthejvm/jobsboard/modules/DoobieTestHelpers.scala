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
  def transactorRsIncludingSetup = transactorRs
    .evalTap { xa =>
      val setupDBQuery = sql"""
              CREATE TABLE jobs(
                  id uuid DEFAULT gen_random_uuid(),
                  date bigint NOT NULL,
                  ownerEmail text NOT NULL,
                  company text NOT NULL,
                  title text NOT NULL,
                  description text NOT NULL,
                  externalUrl text NOT NULL DEFAULT false,
                  remote boolean NOT NULL DEFAULT false,
                  location text NOT NULL,
                  salaryLo integer,
                  salaryHi integer, 
                  currency text,
                  country text,
                  tags text[],
                  image text,
                  seniority text,
                  other text,
                  active BOOLEAN NOT NULL DEFAULT false
              );

              ALTER TABLE jobs
              ADD CONSTRAINT pk_jobs PRIMARY KEY (id);

              SET DEFAULT_TRANSACTION_ISOLATION TO SERIALIZABLE ;
              """.update

      setupDBQuery.run.transact(xa)
    }
    .map(xa => doobie.Transactor.after.set(xa, doobie.HC.rollback))

  private def transactorRs: Resource[IO, doobie.Transactor[IO]] =
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

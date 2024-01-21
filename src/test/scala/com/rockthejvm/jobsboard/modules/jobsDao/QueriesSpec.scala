package com.rockthejvm.jobsboard.modules.jobsDao

import org.testcontainers.containers.PostgreSQLContainer
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import doobie.util.transactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import com.rockthejvm.jobsboard.dto.postgres.job.WriteJob
import com.rockthejvm.jobsboard.domain.job.JobInfo
import com.rockthejvm.jobsboard.domain.job
import com.rockthejvm.jobsboard.modules.DoobieTestHelpers
import doobie.util.update.Update0

object QueriesSpec extends weaver.IOSuite with doobie.weaver.IOChecker {

  override type Res = doobie.Transactor[IO]

  override def sharedResource =
    DoobieTestHelpers
      .postgres
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
              location text,
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

  test("insert job") { implicit xa => check(insertJobQuery) }

  def insertJobQuery = queries
    .insert(
      WriteJob.of(
        jobInfo = JobInfo.empty,
        ownerEmail = "someMail",
        date = 2L,
        active = false
      )
    )

}

package com.rockthejvm.jobsboard.modules

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

object JobsDaoSpec extends weaver.IOSuite with doobie.weaver.IOChecker {

  override type Res = doobie.Transactor[IO]

  override def sharedResource =
    DoobieTestHelpers.postgres
      // .map(xa => doobie.Transactor.after.set(xa, doobie.HC.rollback))

  def jobsDaoRs(xa: doobie.Transactor[IO]) =
    LiveJobsDao(xa)

  test("insert job") { implicit xa =>
    // val dao = LiveJobsDao(xa)

    val jobInfo =
      WriteJob.of(
        jobInfo = JobInfo.empty,
        ownerEmail = "someMail",
        date = 2L,
        active = false
      )

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
      """.update

    val insertJobQuery = sql"""
            INSERT INTO jobs(
                date,
                ownerEmail,
                company,
                title,
                description,
                externalUrl,
                remote,
                location,
                salaryLo,
                salaryHi,
                currency,
                country,
                tags,
                image,
                seniority,
                other,
                active
            ) VALUES (
                $jobInfo
            )
        """.update

    // val queryNumberOfJobs =
    //   sql"""
    //     SELECT COUNT(*)
    //     FROM jobs
    //   """.query[Int]

    setupDBQuery.run.transact(xa) *>
      check(insertJobQuery)
    // insertJobQuery.run.transact(xa) *>
    // queryNumberOfJobs.unique.transact(xa).map(num => assert(num == 1))
  }
}

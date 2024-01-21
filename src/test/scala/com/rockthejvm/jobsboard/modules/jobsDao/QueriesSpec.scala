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
import java.util.UUID

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

  test("insert job query typechecks") { implicit xa => check(insertJobQuery) }
  test("all jobs query typechecks") { implicit xa => check(queries.all) }
  test("find job query typechecks") { implicit xa => check(queries.find(uuidStub)) }
  test("delete job query typechecks") { implicit xa => check(queries.delete(uuidStub)) }
  test("update job query typechecks") { implicit xa =>
    check(
      queries.update(
        uuidStub,
        writeJobStub
      )
    )
  }

  test("all preceded by 0 inserts") { implicit xa =>
    queries.all.to[List].transact(xa).map { jobs => expect(jobs.size == 0) }
  }

  test("all preceded by 1 insert") { implicit xa =>
    (insertJobQuery.run *> queries.all.to[List]).transact(xa).map { jobs =>
      expect(jobs.size == 1)
    }
  }

  test("all preceded by 2 inserts") { implicit xa =>
    (insertJobQuery.run *> insertJobQuery.run *> queries.all.to[List]).transact(xa).map {
      jobs => expect(jobs.size == 2)
    }
  }

  // TODO maybe same as below
  test("insert a job and delete it, returning count of deleted rows == 1") { implicit xa =>
    insertJobQuery
      .withGeneratedKeys[UUID]("id")
      .compile
      .lastOrError
      .flatMap { uuidOfInserted => queries.delete(uuidOfInserted).run }
      .transact(xa)
      .map(deletedRows => expect(deletedRows == 1))
  }

  // TODO maybe dont test this, isntead test client in JobsDao where it should throw in this case
  test("attempt to delete a job with the wrong UUID, returning count of deleted rows == 0") {
    implicit xa =>
      queries.delete(uuidStub).run.transact(xa).map(deletedRows => expect(deletedRows == 0))
  }

  def uuidStub = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

  def writeJobStub = WriteJob.of(
    jobInfo = JobInfo.empty,
    ownerEmail = "someMail",
    date = 2L,
    active = false
  )

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

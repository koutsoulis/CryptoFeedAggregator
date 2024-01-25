package com.rockthejvm.jobsboard.modules.jobsDao.transactionParts

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
    DoobieTestHelpers.transactorRsIncludingSetup

  val uuidStub = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

  val insertJobQuery = queries
    .insert(
      WriteJob.stub
    )

  test("insert job query typechecks") { implicit xa => check(insertJobQuery) }
  test("all jobs query typechecks") { implicit xa => check(queries.all) }
  test("find job query typechecks") { implicit xa => check(queries.find(uuidStub)) }
  test("delete job query typechecks") { implicit xa => check(queries.delete(uuidStub)) }
  test("update job query typechecks") { implicit xa =>
    check(
      queries
        .update(
          uuidStub,
          WriteJob.stub
        )
    )
  }

}

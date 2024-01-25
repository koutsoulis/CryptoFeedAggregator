package com.rockthejvm.jobsboard.modules.jobsDao

import doobie.implicits.*
import doobie.postgres.implicits.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*

import cats.effect.kernel.Resource
import com.rockthejvm.jobsboard.modules.DoobieTestHelpers
import com.rockthejvm.jobsboard.dto.postgres.job as pgDto
import doobie.free.connection.ConnectionIO
import com.rockthejvm.jobsboard.modules.jobsDao.transactionParts.QueriesSpec.uuidStub
import monocle.macros.GenLens
import com.rockthejvm.jobsboard.modules.doobieTestHelpers.EmbeddedPg

object TransactionPartsSpec extends weaver.IOSuite with doobie.weaver.IOChecker {

  override type Res = doobie.Transactor[IO]

  override def sharedResource =
    new DoobieTestHelpers(EmbeddedPg.transactorResource).transactorRsIncludingSetup

  val tParts = TransactionParts.apply

  test("insert a job and delete it; should succeed") { xa =>
    val insertAndDelete =
      tParts.create(pgDto.WriteJob.stub).flatMap { id => tParts.delete(id) }

    insertAndDelete.transact(xa).attempt.map { res => expect(res.isRight) }
  }

  test("attempt to delete a job with the wrong UUID; should raise") { xa =>
    tParts
      .delete(uuidStub)
      .transact(xa).attempt.map { res => expect(res.isLeft) }
  }

  test("insert a job and query for it using the returned uuid; should return the inserted job") { xa =>
    tParts
      .create(pgDto.WriteJob.stub).flatMap { uuid =>
        tParts.find(uuid).tupleRight(uuid)
      }.transact(xa).map { case (maybeReadJob, uuid) =>
        expect(maybeReadJob.contains(pgDto.ReadJob.of(uuid, pgDto.WriteJob.stub)))
      }
  }

  test("insert a job, update it and query for all jobs; should return a single job reflecting the update") { xa =>
    val originalWriteJob = pgDto.WriteJob.stub
    val updatedWriteJob = GenLens[pgDto.WriteJob](_.company).modify(_ ++ "COMPANY NAME UPDATE")(originalWriteJob)

    tParts
      .create(originalWriteJob).flatTap { uuid =>
        tParts.update(uuid, updatedWriteJob)
      }.flatMap { uuid =>
        tParts.all.tupleRight(uuid)
      }.transact(xa).map { case (rows, uuid) =>
        expect(rows.contains(pgDto.ReadJob.of(uuid, updatedWriteJob)))
      }
  }
}

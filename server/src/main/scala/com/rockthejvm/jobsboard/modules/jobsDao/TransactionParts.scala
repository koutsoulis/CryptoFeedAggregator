package com.rockthejvm.jobsboard.modules.jobsDao

import doobie.free.connection.ConnectionIO
import java.util.UUID
import com.rockthejvm.jobsboard.dto.postgres.job as pgDto
import doobie.implicits.*
import doobie.postgres.implicits.*
import transactionParts.queries
import cats.effect.kernel.Sync
import cats.MonadThrow

trait TransactionParts {
  def create(job: pgDto.WriteJob): ConnectionIO[UUID] =
    queries.insert(job).withGeneratedKeys[UUID]("id").compile.onlyOrError

  def all: ConnectionIO[List[pgDto.ReadJob]] =
    queries.all.to[List]

  def find(id: UUID): ConnectionIO[Option[pgDto.ReadJob]] =
    queries.find(id).option

  def update(id: UUID, job: pgDto.WriteJob): ConnectionIO[Unit] =
    queries.update(id, job).run.flatMap { updatedCount =>
      MonadThrow[ConnectionIO].raiseWhen(updatedCount != 1)(new Exception(s"updatedCount == $updatedCount"))
    }

  def delete(id: UUID): ConnectionIO[Unit] =
    queries.delete(id).run.flatMap { deletedCount =>
      MonadThrow[ConnectionIO].raiseWhen(deletedCount != 1)(new Exception(s"deletedCount == $deletedCount"))
    }

}

object TransactionParts {
  def apply: TransactionParts = new TransactionParts {}
}

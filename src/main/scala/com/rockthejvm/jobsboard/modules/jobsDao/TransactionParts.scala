package com.rockthejvm.jobsboard.modules.jobsDao

import doobie.free.connection.ConnectionIO
import java.util.UUID
import com.rockthejvm.jobsboard.dto.postgres.job as pgDto
import doobie.implicits.*
import doobie.postgres.implicits.*
import transactionParts.queries

trait TransactionParts {
  def create(job: pgDto.WriteJob): ConnectionIO[Option[UUID]] =
    queries.insert(job).withGeneratedKeys[UUID]("id").compile.last

  def all: ConnectionIO[List[pgDto.ReadJob]] =
    queries.all.to[List]

  def find(id: UUID): ConnectionIO[Option[pgDto.ReadJob]] =
    queries.find(id).option

  def update(id: UUID, job: pgDto.WriteJob): ConnectionIO[Int] =
    queries.update(id, job).run

  def delete(id: UUID): ConnectionIO[Int] =
    queries.delete(id).run
}

object TransactionParts {
  def apply: TransactionParts = new TransactionParts {}
}

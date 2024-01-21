package com.rockthejvm.jobsboard.modules

import com.rockthejvm.jobsboard.dto
import com.rockthejvm.jobsboard.domain

import doobie.implicits.*
import doobie.postgres.implicits.*
import java.util.UUID
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import com.rockthejvm.jobsboard.dto.postgres.job as pgDto
import jobsDao.queries
// import com.rockthejvm.jobsboard.dto.uuid.*
// import com.rockthejvm.jobsboard.domain.job.uriWitnesses.*

trait JobsDao[F[_]: Concurrent] {
  // algebra
  // CRUD
//   def create(ownerEmail: String, jobInfo: JobInfo): F[Option[UUID]]
  def create(job: pgDto.WriteJob): F[Option[UUID]]
  def all(): F[List[pgDto.ReadJob]]
  def find(id: UUID): F[Option[pgDto.ReadJob]]
  def update(id: UUID, jobInfo: pgDto.WriteJob): F[Int]
  def delete(id: UUID): F[Int]
}

class LiveJobsDao[F[_]: Concurrent] private (xa: doobie.Transactor[F]) extends JobsDao[F] {
  override def create(job: pgDto.WriteJob): F[Option[UUID]] =
    queries.insert(job).withGeneratedKeys[UUID]("id").transact(xa).compile.last

  override def all(): F[List[pgDto.ReadJob]] =
    queries.all.to[List].transact(xa)
  override def find(id: UUID): F[Option[pgDto.ReadJob]] =
    queries.find(id).option.transact(xa)
  override def update(id: UUID, job: pgDto.WriteJob): F[Int] =
    queries.update(id, job).run.transact(xa)

  override def delete(id: UUID): F[Int] =
    queries.delete(id).run.transact(xa)
}

object LiveJobsDao {
  def apply[F[_]: Concurrent](xa: doobie.Transactor[F]): LiveJobsDao[F] = new LiveJobsDao(xa)
}

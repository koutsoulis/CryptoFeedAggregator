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
import jobsDao.transactionParts.queries
import jobsDao.TransactionParts
// import com.rockthejvm.jobsboard.dto.uuid.*
// import com.rockthejvm.jobsboard.domain.job.uriWitnesses.*

trait JobsDao[F[_]: Concurrent] {
  // algebra
  // CRUD
//   def create(ownerEmail: String, jobInfo: JobInfo): F[Option[UUID]]
  def create(job: pgDto.WriteJob): F[Option[UUID]]
  def all: F[List[pgDto.ReadJob]]
  def find(id: UUID): F[Option[pgDto.ReadJob]]
  def update(id: UUID, jobInfo: pgDto.WriteJob): F[Unit]
  def delete(id: UUID): F[Unit]
}

class LiveJobsDao[F[_]: Concurrent] private (
    xa: doobie.Transactor[F],
    transactionparts: TransactionParts)
    extends JobsDao[F] {
  override def create(job: pgDto.WriteJob): F[Option[UUID]] =
    transactionparts.create(job).transact(xa)

  override def all: F[List[pgDto.ReadJob]] =
    transactionparts.all.transact(xa)
  override def find(id: UUID): F[Option[pgDto.ReadJob]] =
    transactionparts.find(id).transact(xa)
  override def update(id: UUID, job: pgDto.WriteJob): F[Unit] =
    transactionparts.update(id, job).transact(xa)

  override def delete(id: UUID): F[Unit] =
    transactionparts.delete(id).transact(xa)
}

object LiveJobsDao {
  def apply[F[_]: Concurrent](xa: doobie.Transactor[F]): LiveJobsDao[F] =
    new LiveJobsDao(xa, TransactionParts.apply)
}

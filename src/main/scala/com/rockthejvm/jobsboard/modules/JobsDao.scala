package com.rockthejvm.jobsboard.modules

import com.rockthejvm.jobsboard.dto
import com.rockthejvm.jobsboard.domain

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*
import java.util.UUID
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*

trait JobsDao[F[_]] {
  // algebra
  // CRUD
//   def create(ownerEmail: String, jobInfo: JobInfo): F[Option[UUID]]
  def create(ownerEmail: String, job: dto.Job.WriteJob): F[Option[UUID]]
  def all(): F[List[dto.Job.ReadJob]]
  def find(id: UUID): F[Option[dto.Job.ReadJob]]
  def update(id: UUID, jobInfo: domain.job.JobInfo): F[Option[dto.Job.ReadJob]]
  def delete(id: UUID): F[Int]
}

/*
id: UUID,
date: Long,
ownerEmail: String,
company: String,
title: String,
description: String,
externalUrl: String,
remote: Boolean,
location: String,
salaryLo: Option[String],
salaryHi: Option[String],
currency: Option[String],
country: Option[String],
tags: Option[List[String]],
image: Option[String],
seniority: Option[String],
other: Option[String],
active: Boolean
 */

class LiveJobsDao[F[_]: Async] private (xa: Transactor[F]) extends JobsDao[F] {
  override def create(ownerEmail: String, job: dto.Job.WriteJob): F[Option[UUID]] =
    sql"""
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
            $job
        )
    """.update.withGeneratedKeys[UUID]("id").transact(xa).compile.last

  override def all(): F[List[dto.Job.ReadJob]] =
    sql"""
        SELECT *
        FROM jobs
    """.query[dto.Job.ReadJob].to[List].transact(xa)
  override def find(id: UUID): F[Option[dto.Job.ReadJob]] =
    sql"""
        SELECT *
        FROM jobs
        WHERE id = $id
    """.query[dto.Job.ReadJob].option.transact(xa)
  override def update(id: UUID, jobInfo: domain.job.JobInfo): F[Option[dto.Job.ReadJob]] =
    sql"""
      INSERT INTO jobs
      VALUES $jobInfo
      ON CONFLICT (id) DO UPDATE
    """.query[dto.Job.ReadJob].option.transact(xa)

  override def delete(id: UUID): F[Int] =
    sql"""
        DELETE
        FROM jobs
        WHERE id = $id
    """.update.run.transact(xa)
}

object LiveJobsDao {
  def apply[F[_]: Async](xa: Transactor[F]): LiveJobsDao[F] = new LiveJobsDao(xa)
}

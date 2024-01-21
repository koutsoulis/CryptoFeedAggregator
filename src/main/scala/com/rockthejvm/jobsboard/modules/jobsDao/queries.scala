package com.rockthejvm.jobsboard.modules.jobsDao

import doobie.implicits.*
import doobie.postgres.implicits.*
import java.util.UUID
import com.rockthejvm.jobsboard.domain
import com.rockthejvm.jobsboard.dto.postgres.job as pgDto
import doobie.free.connection.ConnectionIO
import doobie.util.update.Update0
import doobie.util.query.Query0

object queries {
  def insert(job: pgDto.WriteJob): Update0 =
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
    """.update

  def all: Query0[pgDto.ReadJob] =
    sql"""
        SELECT *
        FROM jobs
    """.query[pgDto.ReadJob]

  def find(id: UUID): Query0[pgDto.ReadJob] =
    sql"""
        SELECT *
        FROM jobs
        WHERE id = $id
    """.query[pgDto.ReadJob]

  def update(id: UUID, job: pgDto.WriteJob): Update0 =
    sql"""
        UPDATE jobs SET(
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
        ) = ($job)
        WHERE id = $id
    """.update

  def delete(id: UUID): Update0 =
    sql"""
        DELETE
        FROM jobs
        WHERE id = $id
    """.update
}

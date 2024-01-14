package com.rockthejvm.jobsboard.dto

import java.util.UUID
import io.scalaland.chimney
import io.scalaland.chimney.syntax.*
import io.scalaland.chimney.dsl.*

import com.rockthejvm.jobsboard.domain
import io.scalaland.chimney.internal.runtime.TransformerCfg.FieldComputed
import io.scalaland.chimney.internal.runtime.Path.Select
import com.rockthejvm.jobsboard.domain.job
import io.scalaland.chimney.internal.runtime.TransformerCfg.Empty
import io.scalaland.chimney.internal.runtime.TransformerFlags.Default
import io.scalaland.chimney.internal.runtime.Path.Root
import io.circe.Encoder
import sttp.model.Uri
import job.Description.given
// import com.rockthejvm.jobsboard.domain.job.uriWitnesses.*

object Job {
  final case class WriteJob(
      date: Long,
      ownerEmail: String,
      company: String,
      title: String,
      description: job.Description,
      externalUrl: String,
      remote: Boolean,
      location: String,
      salaryLo: Option[Int],
      salaryHi: Option[Int],
      currency: Option[String],
      country: Option[String],
      tags: Option[List[String]],
      image: Option[String],
      seniority: Option[String],
      other: Option[String],
      active: Boolean = false
  )

  final case class ReadJob(
      id: UUID,
      date: Long,
      ownerEmail: String,
      company: String,
      title: String,
      description: job.Description,
      externalUrl: String,
      remote: Boolean,
      location: String,
      salaryLo: Option[Int],
      salaryHi: Option[Int],
      currency: Option[String],
      country: Option[String],
      tags: Option[List[String]],
      image: Option[String],
      seniority: Option[String],
      other: Option[String],
      active: Boolean = false
  ) derives Encoder.AsObject

  object WriteJob {
    def of(jobInfo: job.JobInfo, ownerEmail: String, date: Long, active: Boolean): WriteJob =
      chimney
        .Transformer
        .define[job.JobInfo, WriteJob]
        .withFieldConst(_.ownerEmail, ownerEmail)
        .withFieldConst(_.date, date)
        .withFieldConst(_.active, active)
        .buildTransformer
        .transform(jobInfo)
  }

  object ReadJob {
    implicit val toDomain: chimney.Transformer[ReadJob, job.Job] =
      chimney
        .Transformer
        .define[ReadJob, domain.job.Job]
        .withFieldComputed(
          _.jobInfo,
          jobDto => jobDto.into[domain.job.JobInfo].transform
        )
        .buildTransformer
  }

  // def empty: WriteJob = WriteJob(
  //   // id: UUID,
  //   date = 12,
  //   ownerEmail = "",
  //   company = "",
  //   title = "",
  //   description = "",
  //   externalUrl = Uri(""),
  //   remote = false,
  //   location = "",
  //   salaryLo = None,
  //   salaryHi = None,
  //   currency = None,
  //   country = None,
  //   tags = None,
  //   image = None,
  //   seniority = None,
  //   other = None
  // )
}

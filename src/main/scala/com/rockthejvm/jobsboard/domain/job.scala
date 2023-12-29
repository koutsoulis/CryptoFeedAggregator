package com.rockthejvm.jobsboard.domain

import java.util.UUID
// import org.http4s.circe.CirceEntityDecoder
import io.circe.Decoder
import io.circe.generic.semiauto.*

object job {
  case class Job(
      id: UUID,
      date: Long,
      ownerEmail: String,
      jobInfo: JobInfo,
      active: Boolean = false
  )

  case class JobInfo(
      company: String,
      title: String,
      description: String,
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
      other: Option[String]
  )

  object JobInfo {
    implicit val decoder: Decoder[JobInfo] = deriveDecoder[JobInfo].ensure { jobInfo =>
      List(
        Option.when(jobInfo.company.isEmpty())("company should be non empty"),
        Option.when(jobInfo.title.isEmpty())("title should be non empty")
      ).flatten
    }

    val empty: JobInfo =
      JobInfo("", "", "", "", false, "", None, None, None, None, None, None, None, None)

    def minimal(
        company: String,
        title: String,
        description: String,
        externalUrl: String,
        remote: Boolean,
        location: String): JobInfo =
      JobInfo(
        company,
        title,
        description,
        externalUrl,
        remote,
        location,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None)
  }
}

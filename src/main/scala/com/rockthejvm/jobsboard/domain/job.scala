package com.rockthejvm.jobsboard.domain

import java.util.UUID
// import org.http4s.circe.CirceEntityDecoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.Codec
import io.circe.syntax.*
import sttp.tapir.Schema
import sttp.tapir.Validator
import sttp.model.Uri
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.cats.*
import io.scalaland.chimney
import sttp.tapir.Schema.annotations.format
import sttp.tapir.Schema.annotations.validate
import monix.newtypes

object job {
  case class Job(
      id: UUID,
      date: Long,
      ownerEmail: String,
      jobInfo: JobInfo,
      active: Boolean = false
  )

  type Description = Description.Type

  object Description
      extends newtypes.NewtypeWrapped[String]
      with newtypes.integrations.DerivedCirceCodec {
    implicit val schema: Schema[Description] = Schema.string

    given doobie.util.Get[Description] = ???

    given doobie.util.Put[Description] =
      doobie.util.Put[String].contramap(_.value)
  }

  case class JobInfo(
      @validate(Validator.nonEmptyString) company: String,
      title: String,
      description: Description,
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
  ) derives Codec.AsObject

  object JobInfo {
    // implicit val decoder: Decoder[JobInfo] = deriveDecoder[JobInfo].ensure { jobInfo =>
    //   List(
    //     Option.when(jobInfo.company.isEmpty())("company should be non empty"),
    //     Option.when(jobInfo.title.isEmpty())("title should be non empty")
    //   ).flatten
    // }

    implicit val schema: Schema[JobInfo] =
      Schema
        .derived[JobInfo]
        // .validate(Validator.nonEmptyString.contramap(_.company))
        .validate(Validator.nonEmptyString.contramap(_.title))

    val empty: JobInfo =
      JobInfo(
        "",
        "",
        Description.apply(""),
        "https://www.example.com/path/to/resource?query=123#section",
        false,
        "",
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None)

    def minimal(
        company: String,
        title: String,
        description: Description,
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

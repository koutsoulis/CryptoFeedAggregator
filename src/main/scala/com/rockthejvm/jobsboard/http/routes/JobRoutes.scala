package com.rockthejvm.jobsboard.http.routes

// import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.HttpRoutes
import org.http4s.Request
import cats.effect.*
import org.http4s
import org.http4s.dsl.*
import org.http4s.implicits.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import java.util.UUID
import com.rockthejvm.jobsboard.domain.job.Job
import com.rockthejvm.jobsboard.http.responses.FailureResponse
import com.rockthejvm.jobsboard.domain.job.JobInfo
import com.rockthejvm.jobsboard.modules.JobsDao
import com.rockthejvm.jobsboard.domain
import com.rockthejvm.jobsboard.dto.Job.WriteJob
import scala.util.Try
import org.http4s.HttpVersion

class JobRoutes[F[_]: Async] private (jobs: JobsDao[F]) extends Http4sDsl[F] {

  private val allJobsRoute: HttpRoutes[F] = HttpRoutes.of {
    case PUT -> Root / "create" =>
      Ok("TODO")
  }

  private val findJobRoute: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / "find" / UUIDVar(id) =>
      jobs.find(id).flatMap {
        case Some(job) => Ok(job)
        case None => NotFound(FailureResponse(s"job $id not found"))
      }
  }

  private def createJob(jobInfo: JobInfo): F[Job] =
    Job(
      id = UUID.randomUUID(),
      date = System.currentTimeMillis(),
      ownerEmail = "TODO@rockthejvm.com",
      jobInfo = jobInfo,
      active = true
    ).pure

  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of {
    case req @ POST -> Root / "create" =>
      req
        .attemptAs[JobInfo]
        .leftMap(_.toHttpResponse(HttpVersion.`HTTP/1.1`))
        .semiflatMap { jobInfo =>
          val writeJobDto = WriteJob.of(jobInfo, "some@mail.com", 0, false)
          jobs.create("some@email.com", writeJobDto)
        }
        .semiflatMap {
          case Some(id) => Created(id)
          case None => NotFound(FailureResponse(s"Cannot insert"))
        }
        .merge
  }

  // PUT jobs/uuid {jobInfo}
  private val updateJobRoute: HttpRoutes[F] = HttpRoutes.of {
    case req @ PUT -> Root / UUIDVar(id) =>
      for {
        jobInfo <- req.as[domain.job.JobInfo]
        updatedJobDtoOpt <- jobs.update(id, jobInfo)
        resp <- updatedJobDtoOpt match {
          case Some(updatedJobDto) => Ok(updatedJobDto)
          case None => NotFound(FailureResponse(s"Cannot find job with id $id"))
        }
      } yield resp
  }

  // DELETE jobs/uuid
  private val deleteJobRoute: HttpRoutes[F] = HttpRoutes.of {
    case req @ DELETE -> Root / "delete" / UUIDVar(id) =>
      for {
        rowsDeleted <- jobs.delete(id)
        resp <-
          if (rowsDeleted > 0) {
            Ok()
          } else {
            NotFound(FailureResponse(s"Cannot delete job $id: not found"))
          }
      } yield resp
  }

  val endpoints = http4s
    .server
    .Router(
      "/jobs" ->
        (allJobsRoute <+> findJobRoute <+> createJobRoute <+> updateJobRoute <+> deleteJobRoute)
    )

}

object JobRoutes {
  def apply[F[_]: Async](jobs: JobsDao[F]): JobRoutes[F] = new JobRoutes[F](jobs)
}

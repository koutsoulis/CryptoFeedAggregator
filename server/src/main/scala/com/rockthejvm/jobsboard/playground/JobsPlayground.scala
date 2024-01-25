package com.rockthejvm.jobsboard.playground

import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.cats.*
import cats.effect.*
// import doobie.*
// import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*
import doobie.hikari.HikariTransactor
import com.rockthejvm.jobsboard.domain.job.JobInfo
import com.rockthejvm.jobsboard.modules.LiveJobsDao
import com.rockthejvm.jobsboard.dto
import com.rockthejvm.jobsboard.domain
import sttp.model.Uri
import com.rockthejvm.jobsboard.nominals

object JobsPlayground extends IOApp.Simple {

  val postgresResource: Resource[IO, HikariTransactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool(32)
    xa <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql:board",
      "docker",
      "docker",
      ec
    )
  } yield xa

  val jobInfo = JobInfo.minimal(
    company = "Rock the JVM",
    title = "Software Engineer",
    description = nominals.job.Description.apply("some job description"),
    externalUrl = "rockthejvm.com",
    remote = true,
    location = "Anywhere"
  )

  override def run: IO[Unit] = postgresResource.use { xa =>
    // for {
    //   jobs <- LiveJobs[IO](xa)
    //   idOfNewJob <- jobs
    //     .create("mymail", dto.Job.empty)
    //     .flatMap(IO.fromOption(_)(new NoSuchElementException))

    //   jobDto <- jobs.find(idOfNewJob).flatMap(IO.fromOption(_)(new NoSuchElementException))
    //   _ <- IO(println(jobDto.into[domain.job.Job].transform))

    // } yield ()
    ???
  }
}

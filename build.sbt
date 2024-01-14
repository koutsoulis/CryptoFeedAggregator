ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val rockthejvm = "com.rockthejvm"
lazy val scala3Version = "3.3.1"

lazy val circeVersion = "0.14.0"
lazy val catsEffectVersion = "3.3.14"
lazy val munitCEVersion = "2.0.0-M4"
lazy val http4sVersion = "0.23.15"
lazy val tapirVersion = "1.9.6"
lazy val doobieVersion = "1.0.0-RC5"
lazy val chimneyVersion = "0.8.3"
lazy val pureConfigVersion = "0.17.4"
lazy val log4catsVersion = "2.4.0"
lazy val tsecVersion = "0.4.0"
lazy val munitVersion = "0.7.29"
lazy val weaverTestVersion = "0.8.3"
lazy val scalaTestVersion = "3.2.12"
lazy val scalaTestCatsEffectVersion = "1.4.0"
lazy val testContainerVersion = "1.17.3"
lazy val logbackVersion = "1.4.0"
lazy val slf4jVersion = "2.0.0"
lazy val javaMailVersion = "1.6.2"
lazy val catsMtlVersion = "1.3.0"
lazy val apacheCommonsVersion = "1.8.0"
lazy val monixNewtypesVersion = "0.2.3"

import org.typelevel.scalacoptions.ScalacOptions

lazy val server = (project in file(".")).settings(
  name := "typelevel-project",
  scalaVersion := scala3Version,
  organization := rockthejvm,
  scalacOptions ++= Seq("-no-indent"),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-fs2" % circeVersion,
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "org.tpolecat" %% "doobie-weaver" % doobieVersion % Test,
    "io.scalaland" %% "chimney" % chimneyVersion,
    "io.scalaland" %% "chimney-cats" % chimneyVersion,
    "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion,
    "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
    // "org.slf4j" % "slf4j-simple" % slf4jVersion,
    "io.github.jmcardon" %% "tsec-http4s" % tsecVersion,
    "com.sun.mail" % "javax.mail" % javaMailVersion,
    "org.typelevel" %% "log4cats-noop" % log4catsVersion % Test,
    // "org.scalameta" %% "munit" % munitVersion % Test,
    // "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % "test",
    "com.disneystreaming" %% "weaver-cats" % weaverTestVersion % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % scalaTestCatsEffectVersion % Test,
    "org.testcontainers" % "testcontainers" % testContainerVersion % Test,
    "org.testcontainers" % "postgresql" % testContainerVersion % Test,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "org.typelevel" %% "cats-mtl" % catsMtlVersion,
    "commons-validator" % "commons-validator" % apacheCommonsVersion,
    "io.monix" %% "newtypes-core" % monixNewtypesVersion,
    "io.monix" %% "newtypes-circe-v0-14" % monixNewtypesVersion
  ),
  Compile / tpolecatExcludeOptions ++= Set(
    ScalacOptions.warnUnusedImports,
    ScalacOptions.warnUnusedPrivates,
    ScalacOptions.warnUnusedParams,
    ScalacOptions.warnUnusedExplicits,
    ScalacOptions.fatalWarnings
  )
)

enablePlugins(RevolverPlugin)

// mainClass in reStart := Some("com.rockthejvm.jobsboard.playground.JobsPlayground")

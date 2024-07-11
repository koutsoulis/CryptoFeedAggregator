ThisBuild / version := "0.1.1-SNAPSHOT"
// ThisBuild / name := "typelevel-project"

lazy val scala3Version = "3.4.2"

import org.typelevel.scalacoptions.ScalacOptions

///////////////////////////////////////////////////////////////////////////////////////////////////////////
// Common - contains domain model
///////////////////////////////////////////////////////////////////////////////////////////////////////////

lazy val catsEffectVersion = "3.5.4"
lazy val http4sVersion = "0.23.27"
lazy val http4sJDKClientVersion = "0.9.1"
lazy val circeVersion = "0.14.9"
lazy val jsoniterVersion = "2.30.5"
lazy val monocleVersion = "3.2.0"
lazy val fs2Version = "3.10.2"
lazy val chimneyVersion = "1.3.0"
lazy val borerVersion = "1.14.1"

lazy val common = (crossProject(JSPlatform, JVMPlatform) in file("common"))
  .settings(
    name := "common",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-no-indent", "-Vprofile"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.http4s" %%% "http4s-dsl" % http4sVersion,
      "org.http4s" %%% "http4s-circe" % http4sVersion,
      "io.circe" %%% "circe-core" % circeVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-circe" % jsoniterVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "co.fs2" %%% "fs2-scodec" % fs2Version,
      "org.gnieh" %%% "fs2-data-json" % "1.11.0",
      "org.gnieh" %%% "fs2-data-json-circe" % "1.11.0",
      "dev.optics" %%% "monocle-core" % monocleVersion,
      "dev.optics" %%% "monocle-macro" % monocleVersion,
      "io.bullet" %%% "borer-core" % borerVersion,
      "io.bullet" %%% "borer-derivation" % borerVersion,
      "io.bullet" %%% "borer-compat-scodec" % borerVersion,
      "org.typelevel" %%% "mouse" % "1.3.1",
      "io.scalaland" %%% "chimney" % chimneyVersion,
      "io.scalaland" %%% "chimney-cats" % chimneyVersion
    ),
    semanticdbEnabled := true,
    autoAPIMappings := true,
    Compile / tpolecatExcludeOptions ++= Set(
      ScalacOptions.warnUnusedImports,
      ScalacOptions.warnUnusedPrivates,
      ScalacOptions.warnUnusedParams,
      ScalacOptions.warnUnusedLocals,
      ScalacOptions.warnUnusedExplicits,
      ScalacOptions.fatalWarnings
    )
  )
  .jvmSettings(
  )
  .jsSettings(
    // Add JS-specific settings here
  )

///////////////////////////////////////////////////////////////////////////////////////////////////////////
// Frontend
///////////////////////////////////////////////////////////////////////////////////////////////////////////

lazy val tyrianVersion = "0.11.0"
lazy val laikaVersion = "0.19.5"

lazy val app = (project in file("app"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "app",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-Vprofile"),
    libraryDependencies ++= Seq(
      "io.indigoengine" %%% "tyrian-io" % tyrianVersion,
      "org.planet42" %%% "laika-core" % laikaVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "org.http4s" %%% "http4s-dom" % "0.2.11"
    ),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    semanticdbEnabled := true,
    autoAPIMappings := true,
    Compile / tpolecatExcludeOptions ++= Set(
      ScalacOptions.warnUnusedImports,
      ScalacOptions.warnUnusedPrivates,
      ScalacOptions.warnUnusedParams,
      ScalacOptions.warnUnusedLocals,
      ScalacOptions.warnUnusedExplicits,
      ScalacOptions.fatalWarnings
    )
  ).dependsOn(common.js)

lazy val log4catsVersion = "2.7.0"
lazy val weaverTestVersion = "0.8.4"
lazy val logbackVersion = "1.5.6"
lazy val slf4jVersion = "2.0.0"
lazy val prometheus4catsVersion = "2.0.0"

import org.typelevel.scalacoptions.ScalacOptions

lazy val server = (project in file("server"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name := "typelevel-project-backend",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-no-indent", "-Vprofile"),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-ember-client" % http4sVersion,
      "org.http4s" %% "http4s-client-testkit" % http4sVersion,
      "org.http4s" %% "http4s-jdk-http-client" % http4sJDKClientVersion,
      "org.http4s" %% "http4s-prometheus-metrics" % "0.24.7",
      "com.permutive" %% "prometheus4cats" % prometheus4catsVersion,
      "com.permutive" %% "prometheus4cats-java" % prometheus4catsVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
      "org.typelevel" %% "log4cats-noop" % log4catsVersion,
      "com.disneystreaming" %% "weaver-cats" % weaverTestVersion % Test,
      "ch.qos.logback" % "logback-classic" % logbackVersion, // TODO: should be unmanaged I think
      "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test,
      "com.lihaoyi" %% "os-lib" % "0.10.2",
      "com.lihaoyi" %% "pprint" % "0.9.0",
      "io.circe" %%% "circe-parser" % circeVersion
    ),
    semanticdbEnabled := true,
    autoAPIMappings := true,
    Compile / tpolecatExcludeOptions ++= Set(
      // ScalacOptions.warnUnusedImports,
      ScalacOptions.warnUnusedPrivates,
      ScalacOptions.warnUnusedParams,
      ScalacOptions.warnUnusedLocals,
      ScalacOptions.warnUnusedExplicits,
      ScalacOptions.fatalWarnings
    ),
    dockerExposedPorts ++= Seq(4041),
    dockerBaseImage := "amazoncorretto:17-al2023-headless",
    Docker / packageName := "typelevel-project-backend-amd64",
    Docker / daemonUserUid := None,
    Docker / daemonUser := "daemon",
    Docker / dockerRepository := Some("905418066033.dkr.ecr.eu-north-1.amazonaws.com"),
    dockerBuildxPlatforms := Seq("linux/amd64"),
    Docker / dockerUpdateLatest.withRank(KeyRanks.Invisible) := true
    // development environment variables
    // reStart / envVars := Map(
    //   "ENV" -> "development"
    // )
    // reStart / javaOptions += "-Dcats.effect.tracing.buffer.size=128"
  ).dependsOn(common.jvm)

enablePlugins(RevolverPlugin)

mainClass in reStart := Some("MyProjectMain")

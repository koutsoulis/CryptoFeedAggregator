package config

import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import cats.effect.*
import scala.util.Try
import java.nio.file.Path

trait Config {
  def env: Config.ENV
}

object Config {
  class ConfigLive(override val env: Config.ENV) extends Config

  enum ENV {
    case development, production
  }
  object ENV {
    given ciris.ConfigDecoder[String, ENV] = ciris.ConfigDecoder[String].mapOption("ENV") { string =>
      Try(ENV.valueOf(string)).toOption
    }
  }

  def apply[F[_]: Async](): F[Config] = {
    ciris
      .env("ENV").as[ENV].map { env =>
        new ConfigLive(env)
      }.load
  }
}

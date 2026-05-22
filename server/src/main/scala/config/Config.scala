package config

import _root_.io.circe
import cats.*
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Uri
import org.http4s.headers.Origin

case class Config private (
    host: Host,
    port: Port,
    corsAllowedOrigins: Set[Origin.Host]
) derives circe.Decoder

private given hostDecoder: circe.Decoder[Host] = circe.Decoder[String].emap(Host.fromString.map(_.toRight(left = "could not decode Host")))
private given portDecoder: circe.Decoder[Port] = circe.Decoder[Int].emap(Port.fromInt.map(_.toRight(left = "could not decode Port")))
private given originHostDecoder: circe.Decoder[Origin.Host] = circe.Decoder[String].emap(Config.parseOriginHost)

object Config {
  def load[F[_]: Async]: F[Config] = {
    Async[F]
      .blocking(
        os.read(os.resource / "configuration.json")
      ).map(circe.parser.decode[Config]).rethrow.flatMap { config =>
        Async[F].fromEither(
          sys.env
            .get("CORS_ALLOWED_ORIGINS")
            .filter(_.nonEmpty)
            .traverse(parseOriginHosts)
            .map(_.fold(config)(origins => config.copy(corsAllowedOrigins = origins)))
        )
      }
  }

  private def parseOriginHosts(raw: String): Either[Throwable, Set[Origin.Host]] =
    raw
      .split(',')
      .iterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .toList
      .traverse(parseOriginHost)
      .map(_.toSet)
      .leftMap(new IllegalArgumentException(_))

  private[config] def parseOriginHost(raw: String): Either[String, Origin.Host] =
    Uri.fromString(raw).leftMap(_.message).flatMap { uri =>
      (
        uri.scheme.toRight(s"CORS origin '$raw' must include a scheme"),
        uri.host.toRight(s"CORS origin '$raw' must include a host")
      ).mapN { (scheme, host) =>
        Origin.Host(scheme, host, uri.port)
      }
    }
}

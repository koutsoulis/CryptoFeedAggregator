package config

import _root_.io.circe
import cats.*
import cats.effect.*
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port

case class Config private (
    host: Host,
    port: Port
) derives circe.Decoder

private given circe.Decoder[Host] = circe.Decoder[String].emap(Host.fromString.map(_.toRight(left = "could not decode Host")))
private given circe.Decoder[Port] = circe.Decoder[Int].emap(Port.fromInt.map(_.toRight(left = "could not decode Port")))

object Config {
  def load[F[_]: Async]: F[Config] = {
    Async[F]
      .blocking(
        os.read(os.resource / "configuration.json")
      ).map(circe.parser.decode).rethrow
  }
}

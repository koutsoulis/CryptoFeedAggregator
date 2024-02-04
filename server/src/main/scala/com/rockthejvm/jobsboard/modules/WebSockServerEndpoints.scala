package com.rockthejvm.jobsboard.modules

// import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.HttpRoutes
import org.http4s.Request
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import java.util.UUID
import com.rockthejvm.jobsboard.domain.job.Job
import com.rockthejvm.jobsboard.http.responses.FailureResponse
import com.rockthejvm.jobsboard.domain.job.JobInfo
import com.rockthejvm.jobsboard.modules.JobsDao
import com.rockthejvm.jobsboard.domain
import com.rockthejvm.jobsboard.dto.postgres.job.WriteJob
import scala.util.Try
import org.http4s.HttpVersion
import sttp.tapir
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import ch.qos.logback.core.status.Status
import io.circe.Codec
import sttp.tapir.server.ServerEndpoint
import sttp.capabilities.fs2.Fs2Streams
import java.nio.charset.StandardCharsets
import fs2.Chunk
import com.rockthejvm.Example
import concurrent.duration.DurationInt
import com.rockthejvm.Example.WrappedString
import sttp.capabilities.WebSockets
// import sttp.tapir.CodecFormat.Json.

class WebSockServerEndpoints[F[_]: Async] {

  val simpleServerEndpointWS: ServerEndpoint[Fs2Streams[F] & WebSockets, F] {
    type SECURITY_INPUT = Unit; type PRINCIPAL = Unit; type INPUT = Unit; type ERROR_OUTPUT = Unit;
    type OUTPUT = fs2.Stream[F, String] => fs2.Stream[F, WrappedString]
  } = {
    val simpleEndpoint: PublicEndpoint[
      Unit,
      Unit,
      fs2.Stream[F, String] => fs2.Stream[F, Example.WrappedString],
      Fs2Streams[F] & sttp.capabilities.WebSockets] = tapir
      .endpoint
      .get
      .in("simpleWS")
      .out(statusCode(StatusCode(200)))
      .out(webSocketBody[String, CodecFormat.TextPlain, Example.WrappedString, CodecFormat.Json](Fs2Streams[F]))
    // .out(streamBody(Fs2Streams[F])(Schema.any[Example.WrappedString], CodecFormat.Json()))

    simpleEndpoint
      .serverLogicSuccess { _ =>
        val iterator = Iterator
          .iterate(0) { _ + 1 }.map(s"string number " ++ _.toString()).map(Example.WrappedString.apply)

        val stream = fs2.Stream.fromIterator.apply(iterator, 1).metered(1.seconds)
        scala.Function.const(stream).pure[F]
      }
  }

}

object WebSockServerEndpoints {
  def apply[F[_]: Async](): WebSockServerEndpoints[F] = new WebSockServerEndpoints[F]()
}

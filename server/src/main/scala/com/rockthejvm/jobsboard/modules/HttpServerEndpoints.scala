package com.rockthejvm.jobsboard.modules

import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.effect.*
import org.http4s.HttpRoutes
import com.rockthejvm.jobsboard.http.routes
import com.rockthejvm.jobsboard.domain.job.JobInfo
import sttp.tapir.server.ServerEndpoint
import sttp.tapir
import sttp.tapir.*
import sttp.tapir.json.circe.*
import java.util.UUID
import com.rockthejvm.jobsboard.http.routes.JobServerEndpoints
import scala.collection.immutable.ListMap
import sttp.tapir.EndpointInput
import sttp.tapir.EndpointInput.Auth
import sttp.tapir.EndpointInput.AuthType.OAuth2
import sttp.model.StatusCode
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import com.rockthejvm.jobsboard.modules.HttpServerEndpoints.SecurityErr1
import io.circe
import io.circe.generic.semiauto.*
import sttp.tapir.EndpointIO.annotations.path
import sttp.client3.*
import sttp.client3.http4s.*
import java.time.Instant
import sttp.tapir.Schema
import sttp.capabilities.fs2.Fs2Streams
import org.http4s.headers.Server

trait HttpServerEndpoints[F[_]: Async] {
  def endpoints: ServerEndpoint[Any, F] {
    type SECURITY_INPUT = Unit; type PRINCIPAL = Unit; type INPUT = Unit; type ERROR_OUTPUT = Unit;
    type OUTPUT = Unit
  }
}

object HttpServerEndpoints {
  def apply[F[_]: Async](jobs: JobsDao[F]): HttpServerEndpoints[F] = new Live[F](jobs)

  // def stub[F[_]: Async]: HttpServerEndpoints[F] = new HttpServerEndpoints {
  //   override def endpoints: ServerEndpoint[Any, F] {
  //     type SECURITY_INPUT = Unit; type PRINCIPAL = Unit; type INPUT = Unit; type ERROR_OUTPUT = Unit;
  //     type OUTPUT = Unit
  //   } = ServerEndpoint.public(Endpoint.)
  // }

  class Live[F[_]: Async](jobs: JobsDao[F]) extends HttpServerEndpoints[F] {
    val accessTokenUrl = "https://github.com/login/oauth/access_token"
    val authorizationUrl = "https://github.com/login/oauth/authorize"
    val clientId: String = "fc8eda555072c89325e2"
    val clientSecret: String = "12f510af60f7bb6f6b94796fe26cad5fbd7cf730"
    val jwtAlgo = JwtAlgorithm.HS256
    val jwtKey = "my secret key"

    // def oAuthRedirectRoute(backend: SttpBackend[F, Any]): ServerEndpoint[Any, F] = {
    //   val oAuthRedirectEndpoint =
    //     tapir
    //       .endpoint
    //       .get
    //       .in("callback")
    //       .in(query[String]("code"))
    //       .out(jsonBody[AccessDetails])
    //       .errorOut(stringBody)

    //   oAuthRedirectEndpoint.serverLogic { code =>
    //     basicRequest
    //       .response(asStringAlways)
    //       .post(uri"$accessTokenUrl?client_id=$clientId&client_secret=$clientSecret&code=$code")
    //       .header("Accept", "application/json")
    //       .send(backend)
    //       .map { resp =>
    //         // create jwt token, that client will use for authenticating to the app
    //         val now = Instant.now
    //         val claim =
    //           JwtClaim(
    //             expiration = Some(now.plusSeconds(15 * 60).getEpochSecond),
    //             issuedAt = Some(now.getEpochSecond),
    //             content = resp.body)
    //         AccessDetails(JwtCirce.encode(claim, jwtKey, jwtAlgo)).asRight[String]
    //       }
    //   }
    // }

    // def loginRoute: ServerEndpoint[Any, F] = {
    //   tapir
    //     .endpoint
    //     .get
    //     .in("login")
    //     .out(statusCode(StatusCode.PermanentRedirect))
    //     .out(header[String]("Location"))
    //     .serverLogicSuccess(_ => Async[F].pure(s"$authorizationUrl?client_id=$clientId"))
    // }

    val endpoints = {
      val inputAuth: Auth[String, OAuth2] = tapir
        .auth
        .oauth2
        .authorizationCode(
          authorizationUrl = Some("https://github.com/login/oauth/authorize"),
          tokenUrl = Some(accessTokenUrl)
        )

      val jobRoutes =
        ???

      val res = jobRoutes
      // .prependSecurity(
      //   additionalSecurityInput = inputAuth,
      //   securityErrorOutput = tapir.oneOf[SecurityErr](
      //     tapir.oneOfVariant(StatusCode(401), jsonBody[SecurityErr1])
      //   )
      // )(
      //   additionalSecurityLogic = { token =>
      //     // Async[F].pure(().asRight[SecurityErr1.type | SecurityErr2.type])
      //     Async[F].pure {
      //       JwtCirce
      //         .decodeAll(token, jwtKey, Seq(jwtAlgo))
      //         .toEither
      //         .left
      //         .map(cause => SecurityErr1(cause.getMessage()))
      //         .void
      //     }
      //   }
      // )

      res
    }

  }

  trait SecurityErr
  case class SecurityErr1(cause: String) extends SecurityErr derives circe.Codec.AsObject, tapir.Schema
  case object SecurityErr2 extends SecurityErr derives circe.Codec.AsObject, tapir.Schema
  // case object SecurityErr3 derives circe.Codec.AsObject, tapir.Schema

  case class AccessDetails(
      @Schema.annotations.description("clarifying description of what token is")
      token: String
  ) derives circe.Codec.AsObject,
        tapir.Schema
}

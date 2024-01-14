package com.rockthejvm.jobsboard.http.responses

import io.circe.Encoder
import io.circe.generic.semiauto.*

final case class FailureResponse(error: String)

object FailureResponse {
  implicit val fooDecoder: Encoder[FailureResponse] = deriveEncoder[FailureResponse]
}

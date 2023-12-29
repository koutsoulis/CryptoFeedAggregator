package com.rockthejvm.jobsboard.http.responses

import io.circe.Encoder

final case class FailureResponse(error: String) derives Encoder.AsObject

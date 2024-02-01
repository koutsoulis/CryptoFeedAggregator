package com.rockthejvm

import io.circe
import io.circe.generic.semiauto.*
import fs2.data.json.circe.*
import fs2.data.json.selector.*
import fs2.data.json.*
import cats.effect.IO
import fs2.Fallible
import cats.effect.*
import cats.MonadError
// import sttp.tapir.Codec.JsonCodec
// import sttp.tapir.Codec
// import sttp.tapir.CodecFormat.Json
// import sttp.tapir.json.circe.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.Schema

object Example {
  case class WrappedString(value: String) derives circe.Codec.AsObject, Schema

  // object WrappedString {
  //   // implicit val tapirCodec = Codec.derivedEnumeration
  //   implicitly[JsonCodec[WrappedString]]
  // }

  def asd = WrappedString("")
  def asdStream = fs2.Stream(asd).covary[IO]
  def input = fs2
    .Stream("""{
    "field1": 0,
    "field2": "test",
    "field3": [1, 2, 3]
    }
    {
    "field1": 2,
    "field3": []
  }""").covary[IO]

  def input2 = asdStream.through(codec.serialize[IO, WrappedString])

  // def input3 = input2.through(render.compact).through(fs2.text.base64)

  // def mystream[F[_]: MonadError]: fs2.Stream[F, WrappedString] = fs2.Stream.emit(asd).covary[F]
  // def stream1: Stream[MonadError, Token] = ???

}

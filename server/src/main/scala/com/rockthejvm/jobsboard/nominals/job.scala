package com.rockthejvm.jobsboard.nominals

import monix.newtypes
import sttp.tapir.Schema

object job {
  type Description = Description.Type

  object Description
      extends newtypes.NewtypeWrapped[String]
      with newtypes.integrations.DerivedCirceCodec {
    implicit val schema: Schema[Description] = Schema.string

    given doobie.util.Get[Description] =
      doobie.util.Get[String].map(Description.apply)

    given doobie.util.Put[Description] =
      doobie.util.Put[String].contramap(_.value)
  }
}

package com.rockthejvm.jobsboard.playground

import cats.*
import cats.data.*
import cats.syntax.all.*
// import cats.mtl.implicits.*
// import cats.mtl.syntax.*

object SimplePlayground {
  sealed trait Error1
  sealed trait Error2
  sealed trait Error3
  case object Error4

//   def throwsTwoErrors[F[_]: Monad](fInt: F[Int])(implicit
//       impl1: cats.mtl.Handle[F, Error1],
//       impl2: cats.mtl.Handle[F, Error2],
//       impl3: cats.mtl.Raise[F, Error3]
//   ) = fInt.handle[Error1](asd => 1)

  def throwsTwoErrors[F[_]: Monad](
      fInt: F[Either[Error1 | Error2 | Error3, Int]]
  ) = {
    fInt.map { either =>
      either.leftFlatMap {
        case _: Error1 => Either.left(Error4)
        case e: (Error3 | Error2) => Either.left(e)
      }
    }
  }
}

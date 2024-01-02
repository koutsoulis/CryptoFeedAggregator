package com.rockthejvm.jobsboard.dto

import doobie.util.meta.Meta
import java.util.UUID
import doobie.util.Write
import doobie.util.Read

//unused
object uuid {
  private val nonImplWrite = {
    import doobie.postgres.implicits.*
    summon[Write[UUID]]
  }

  implicit val implWrite: Write[UUID] = nonImplWrite

  private val nonImplRead = {
    import doobie.postgres.implicits.*
    summon[Read[UUID]]
  }

  implicit val implRead: Read[UUID] = nonImplRead
}

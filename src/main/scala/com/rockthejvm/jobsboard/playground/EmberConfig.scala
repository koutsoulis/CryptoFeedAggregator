package com.rockthejvm.jobsboard.playground

import com.comcast.ip4s.{Host, Port}
import pureconfig.*
import pureconfig.generic.derivation.default.*


case class EmberConfig(host: Host, port: Port)

object EmberConfig{
  given ConfigReader[EmberConfig] = ConfigReader.derived
  
  given ConfigReader[Host] = ConfigReader.fromString{string =>
    Host.fromString(string).toRight(error.CannotConvert(string, "ip4s.Host", ""))
  }

  given ConfigReader[Port] = ConfigReader.fromString { string =>
    Port.fromString(string).toRight(error.CannotConvert(string, "ip4s.Port", ""))
  }
}
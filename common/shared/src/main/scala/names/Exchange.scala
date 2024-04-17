package names

enum Exchange {
  case Binance
}

object Exchange {
  def unapply(s: String): Option[Exchange] = scala.util.Try(Exchange.valueOf(s)).toOption
}

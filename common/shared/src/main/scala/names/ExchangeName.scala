package names

enum ExchangeName {
  case Binance
}

object ExchangeName {
  def unapply(s: String): Option[ExchangeName] = scala.util.Try(ExchangeName.valueOf(s)).toOption
}

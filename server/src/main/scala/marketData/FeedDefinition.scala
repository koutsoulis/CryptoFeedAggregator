package marketData

sealed trait FeedDefinition[M] {
  type Message = M
}

object FeedDefinition {
  case class Level2(currency1: Currency, currency2: Currency) extends FeedDefinition[Level2.Message]

  object Level2 {
    case class Message(buys: List[(BigDecimal, Int)], sells: List[(BigDecimal, Int)])
  }
}

case class Currency(name: String)

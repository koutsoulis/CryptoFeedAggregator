package marketData.exchange.impl.binance.dto

import weaver.SimpleIOSuite
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*

object ExchangeInfoSpec extends SimpleIOSuite {
  test("deserializes ExchangeInfo") {
    IO.delay {
      circe.parser.decode[ExchangeInfo](os.read(os.resource / "binance-exchange-info.json"))
    }.map { result => expect(result.isRight) }
  }
}

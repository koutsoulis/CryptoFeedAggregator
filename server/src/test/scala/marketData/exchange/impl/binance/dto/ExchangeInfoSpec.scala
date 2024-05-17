package marketData.exchange.impl.binance.dto

import _root_.io.circe
import cats.*
import cats.effect.*
import weaver.SimpleIOSuite

object ExchangeInfoSpec extends SimpleIOSuite {
  test("deserializes ExchangeInfo") {
    IO.blocking {
      circe.parser.decode[ExchangeInfo](os.read(os.resource / "binance-exchange-info.json"))
    }.map { result => expect(result.isRight) }
  }
}

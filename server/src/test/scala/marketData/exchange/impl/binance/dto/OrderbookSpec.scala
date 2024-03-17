package marketData.exchange.impl.binance.dto

import weaver.SimpleIOSuite

import _root_.io.circe
import _root_.io.circe.generic.semiauto.*

import cats.effect.*

object OrderbookSpec extends SimpleIOSuite {
  val jsonString = """
    {
        "lastUpdateId": 44624398257,
        "bids": [
            [
                "63512.00000000",
                "1.21353000"
            ],
            [
                "63511.78000000",
                "0.00011000"
            ]
        ],
        "asks": [
            [
                "63512.01000000",
                "4.56791000"
            ],
            [
                "63512.47000000",
                "0.34640000"
            ]
        ]
    }
  """

  test("deserializes binance Orderbook snapshot") {
    IO {
      circe.parser.decode[Orderbook](jsonString)
    }.map(_.isRight).map(expect(_))
  }
}

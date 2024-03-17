package marketData.exchange.impl.binance

import weaver.SimpleIOSuite
import _root_.io.circe
import _root_.io.circe.generic.semiauto.*
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*

object dtoSpec extends SimpleIOSuite {
  val jsonString = """
  {
      "timezone": "UTC",
      "serverTime": 1710678362357,
      "rateLimits": [
          {
              "rateLimitType": "REQUEST_WEIGHT",
              "interval": "MINUTE",
              "intervalNum": 1,
              "limit": 6000
          },
          {
              "rateLimitType": "ORDERS",
              "interval": "SECOND",
              "intervalNum": 10,
              "limit": 100
          },
          {
              "rateLimitType": "ORDERS",
              "interval": "DAY",
              "intervalNum": 1,
              "limit": 200000
          },
          {
              "rateLimitType": "RAW_REQUESTS",
              "interval": "MINUTE",
              "intervalNum": 5,
              "limit": 61000
          }
      ]
  }
  """

  test("deserializes ratelimit info") {
    IO.delay {
      circe.parser.decode[dto.ExchangeInfo](jsonString)
    }.rethrow.flatTap(IO.print).map(_ => expect(true))
  }
}

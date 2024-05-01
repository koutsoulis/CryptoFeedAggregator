package marketData

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.exchange.impl.Binance
import org.http4s
import concurrent.duration.DurationInt
import marketData.domain.Orderbook
import fs2.Stream
import marketData.names.Currency
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.Logger
import marketData.names.TradePair

object MarketDataServiceIT extends SimpleIOSuite {
  val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
  given Logger[IO] = loggerFactory.getLogger
  val mdService: Resource[IO, MarketDataService[IO]] = {
    (http4s.ember.client.EmberClientBuilder.default.build, Resource.eval(http4s.jdkhttpclient.JdkWSClient.simple))
      .flatMapN { (httpClient, wsClient) =>
        Resource.eval(Binance.apply[IO](httpClient, wsClient))
      }
      .evalMap(MarketDataService.apply(_, ???))
  }

  test("bs test") {
    mdService.use { mdService =>
      val res: IO[Unit] = mdService
        .stream(names.FeedName.OrderbookFeed(TradePair(Currency("ETH"), Currency("BTC"))))
        .metered[IO](5.seconds).take(3)
        .evalMap { ob =>
          IO.println(ob.askLevelToQuantity.head)
        }.compile.drain

      res *>
        IO.pure(expect(true))
    }
  }
}

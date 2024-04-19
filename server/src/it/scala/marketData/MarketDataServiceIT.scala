package marketData

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.exchange.impl.Binance
import org.http4s
import concurrent.duration.DurationInt
import marketData.exchange.impl.binance.domain.Orderbook
import fs2.Stream

object MarketDataServiceIT extends SimpleIOSuite {
  val mdService: Resource[IO, MarketDataService[IO]] = {
    (http4s.ember.client.EmberClientBuilder.default.build, Resource.eval(http4s.jdkhttpclient.JdkWSClient.simple))
      .flatMapN { (httpClient, wsClient) =>
        Resource.eval(Binance.apply[IO](httpClient, wsClient))
      }
      .evalMap(MarketDataService.apply)
  }

  test("bs test") {
    mdService.use { mdService =>
      val res: IO[Unit] = mdService
        .stream[Orderbook](FeedName.OrderbookFeed(Currency("ETH"), Currency("BTC")))
        .metered[IO](5.seconds).take(3)
        .evalMap { ob =>
          IO.println(ob.lastUpdateId)
            *> IO.println(ob.askLevelToQuantity.head)
        }.compile.drain

      res *>
        IO.pure(expect(true))
    }
  }
}

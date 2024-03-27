package marketData

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import marketData.exchange.impl.Binance
import org.http4s
import concurrent.duration.DurationInt

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
      mdService
        .stream(FeedDefinition.OrderbookFeed(Currency("ETH"), Currency("BTC"))).evalMap { ob =>
          IO.println(ob.lastUpdateId)
        }.metered(1.seconds).take(3).compile.drain *>
        IO.pure(expect(true))
    }
  }
}

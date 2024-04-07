import cats.Monad
import cats.effect.*
import cats.effect.IO.{IOCont, Uncancelable}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import scala.util.chaining.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import marketData.exchange.ExchangeSpecific
import server.metrics.Metrics
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}
import cats.*
import cats.data.*
import cats.syntax.all.*

object Main extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    val serverResource = for {
      httpClient <- EmberClientBuilder.default[IO].build
      wsClient <- JdkWSClient.simple[IO].toResource
      binanceSpecific <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient).toResource
      marketDataService <- marketData.MarketDataService.apply[IO](binanceSpecific).toResource
      metrics <- Metrics.apply[IO]
      server <- _root_.server.Server[IO](marketDataService, metrics)
    } yield server

    serverResource.useForever.as(())
    // (EmberClientBuilder.default[IO].build, JdkWSClient.simple[IO].toResource)
    //   .flatMapN(
    //     marketData
    //       .exchange.impl.Binance.apply[IO](_, _).toResource
    //   ).flatMap { exchangeSpecific =>
    //     marketData.MarketDataService.apply[IO](exchangeSpecific).toResource
    //   }.flatMap { mdService =>
    //     _root_.server.Server[IO](mdService)
    //   }.useForever.as(())
  }
}

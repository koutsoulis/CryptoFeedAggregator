import cats.effect.*
import org.http4s.ember.client.EmberClientBuilder
import pureconfig.ConfigReader.Result
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.http4s.jdkhttpclient.JdkWSClient
import cats.*
import cats.data.*
import cats.syntax.all.*
import myMetrics.MyMetrics

object Main extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    val serverResource = for {
      httpClient <- EmberClientBuilder.default[IO].build
      wsClient <- JdkWSClient.simple[IO].toResource
      binanceSpecific <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient).toResource
      marketDataService <- marketData.MarketDataService.apply[IO](binanceSpecific).toResource
      (metricsExporter, metricsRegister) <- MyMetrics.apply[IO]
      server <- _root_.server.Server[IO](marketDataService, metricsExporter, metricsRegister)
    } yield server

    serverResource.useForever.as(())
  }
}

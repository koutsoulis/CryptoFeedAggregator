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
import names.ExchangeName
import org.http4s.client.middleware.Logger

object MyProjectMain extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    val serverResource = for {
      logger <- loggerFactory.create.toResource
      httpClient <- EmberClientBuilder.default[IO].build.map(Logger.apply[IO](false, false))
      wsClient <- JdkWSClient.simple[IO].toResource
      binanceSpecific <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient)(using logger).toResource
      marketDataService <- marketData.MarketDataService.apply[IO](binanceSpecific).toResource
      marketDataServiceByExchange = Map(ExchangeName.Binance -> marketDataService)
      (metricsExporter, metricsRegister) <- MyMetrics.apply[IO]
      server <- _root_.server.Server[IO](marketDataServiceByExchange, metricsExporter, metricsRegister)
    } yield server

    serverResource.useForever.as(())
  }
}

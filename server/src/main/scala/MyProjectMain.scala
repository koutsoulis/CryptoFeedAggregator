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
import org.http4s.client.middleware
import _root_.server.Server
import marketData.exchange.Exchange
import marketData.MarketDataService
import fs2.io.net.Network
import servingRoutes.ServingRoutes
import org.typelevel.log4cats.Logger

object MyProjectMain extends IOApp.Simple {
  override def run: IO[Unit] = {
    val serverResource: Resource[IO, Server] = for {
      given Logger[IO] <- Slf4jFactory.create[IO].create.toResource
      httpClient <- EmberClientBuilder.default[IO].build.map(middleware.Logger.apply[IO](false, false))
      wsClient <- JdkWSClient.simple[IO].toResource
      binance <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient).toResource
      coinbase <- marketData.exchange.impl.Coinbase.apply[IO](wsClient, httpClient).toResource
      (metricsExporter, outgoingConcurrentStreamsGauge, incomingConcurrentStreamsGauge) <- MyMetrics.apply[IO]
      marketDataServiceByExchange: Map[ExchangeName, MarketDataService[IO]] <-
        List(binance, coinbase)
          .traverse { exchange =>
            marketData
              .MarketDataService.apply[IO](exchange, incomingConcurrentStreamsGauge).map(exchange.name -> _)
          }.toResource.map(_.toMap)

      server <- Server[IO](
        servingRoutes = ServingRoutes(marketDataServiceByExchange, outgoingConcurrentStreamsGauge),
        metricsExporter = metricsExporter
      )
    } yield server

    serverResource.useForever.as(())
  }
}

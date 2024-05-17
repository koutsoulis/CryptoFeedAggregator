import _root_.server.Server
import cats.*
import cats.effect.*
import cats.syntax.all.*
import fs2.io.net.Network
import marketData.MarketDataService
import marketData.exchange.Exchange
import myMetrics.MyMetrics
import names.ExchangeName
import org.http4s.client.middleware
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.jdkhttpclient.JdkWSClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import servingRoutes.ServingRoutes
import config.Config

object MyProjectMain extends IOApp.Simple {
  override def run: IO[Unit] = {
    val serverResource: Resource[IO, Server] = for {
      given Logger[IO] <- Slf4jFactory.create[IO].create.toResource
      config <- Config.load[IO].toResource
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
          }.map(_.toMap)

      server <- Server[IO](
        servingRoutes = ServingRoutes(marketDataServiceByExchange, outgoingConcurrentStreamsGauge),
        metricsExporter = metricsExporter,
        config = config
      )
    } yield server

    serverResource.useForever.as(())
  }
}

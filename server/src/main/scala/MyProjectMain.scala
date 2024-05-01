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
import _root_.server.Server
import marketData.exchange.ExchangeSpecific
import marketData.MarketDataService

object MyProjectMain extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    val serverResource: Resource[IO, Server[IO]] = for {
      logger <- loggerFactory.create.toResource
      httpClient <- EmberClientBuilder.default[IO].build.map(Logger.apply[IO](false, false))
      wsClient <- JdkWSClient.simple[IO].toResource
      binance <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient)(using logger).toResource
      coinbase <- marketData.exchange.impl.Coinbase.apply[IO](wsClient, httpClient)(using logger).toResource
      (metricsExporter, outgoingConcurrentStreamsGauge, incomingConcurrentStreamsGauge) <- MyMetrics.apply[IO](using Async[IO], logger)
      marketDataServiceByExchange: Map[ExchangeName, MarketDataService[IO]] <-
        List(binance, coinbase)
          .traverse { exchange =>
            marketData
              .MarketDataService.apply[IO](exchange, incomingConcurrentStreamsGauge).map(exchange.name -> _)
          }.toResource.map(_.toMap)

      server <- Server[IO](marketDataServiceByExchange, metricsExporter, outgoingConcurrentStreamsGauge)
    } yield server

    serverResource.useForever.as(())
  }
}

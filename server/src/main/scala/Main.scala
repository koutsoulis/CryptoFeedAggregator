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
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}
import cats.*
import cats.data.*
import cats.syntax.all.*
import myMetrics.MyMetrics

object Main extends IOApp.Simple {
  override def run: IO[Unit] = {
    implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
    val serverResource = for {
      httpClient <- Resource.make(IO.unit)(_ => IO.println("ember client released")) *> (EmberClientBuilder.default[IO].build)
      wsClient <- JdkWSClient.simple[IO].toResource
      binanceSpecific <- marketData.exchange.impl.Binance.apply[IO](httpClient, wsClient).toResource
      marketDataService <- marketData.MarketDataService.apply[IO](binanceSpecific).toResource
      (metricsExporter, metricsRegister) <-
        Resource.make(IO.unit)(_ => IO.println("MyMetrics service released")) *> (MyMetrics.apply[IO])
      server <-
        Resource
          .make(IO.unit)(_ => IO.println("server released")) *> (_root_
          .server.Server[IO](marketDataService, metricsExporter, metricsRegister))
    } yield server

    // Resource.make(IO.unit)(_ => IO.unit).useForever

    serverResource.useForever.as(())
  }
}

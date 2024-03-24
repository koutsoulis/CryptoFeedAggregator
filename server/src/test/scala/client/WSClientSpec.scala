package client

import weaver.SimpleIOSuite
import org.http4s
import org.http4s.client.websocket
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.Uri
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s.client.websocket.WSConnectionHighLevel
import org.http4s.client.websocket.WSRequest
import concurrent.duration.DurationInt
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Close
import cats.effect.kernel.DeferredSource
import fs2.Stream
import org.http4s.client.websocket.WSFrame
import scala.concurrent.duration.Duration
import org.http4s.client.websocket.WSClientHighLevel
import cats.effect.std.Semaphore
import client.rateLimits.RLSemaphoreAndReleaseTime
import cats.effect.testkit.TestControl
import _root_.io.circe

object WSClientSpec extends SimpleIOSuite {

  val stubBackingClient: IO[(WSClientHighLevel[IO], Ref[IO, Vector[Duration]])] = IO.ref(Vector.empty[Duration]).map { receptionTimes =>
    val backingClient = new websocket.WSClientHighLevel[IO] {

      override def connectHighLevel(request: WSRequest): Resource[IO, WSConnectionHighLevel[IO]] =
        Resource.make(
          acquire = IO.pure(
            new WSConnectionHighLevel[IO] {

              override def receiveStream: Stream[IO, WSDataFrame] = Stream
                .fromIterator[IO](
                  Iterator.from(0).map(_.toString()).map { str => WSFrame.Text(str) },
                  1
                )

              override def receive: IO[Option[WSDataFrame]] = ???

              override def send(wsf: WSDataFrame): IO[Unit] = ???

              override def closeFrame: DeferredSource[IO, Close] = ???

              override def sendMany[G[_$8]: Foldable, A <: WSDataFrame](wsfs: G[A]): IO[Unit] = ???

              override def subprotocol: Option[String] = ???

            }
          ) <* IO.realTime.flatMap { currentTime =>
            receptionTimes.update(_.appended(currentTime))
          }
        )(
          release = _ => IO.unit
        )

    }

    backingClient -> receptionTimes
  }

  test("respects rate limits") {
    val rateLimitPermits = 10
    val permitCostPerRequest = 1
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time

    def clientUnderTestAndReceptionTimes = (
      stubBackingClient,
      Semaphore[IO](rateLimitPermits).map { sem => RLSemaphoreAndReleaseTime(sem, rateLimitPeriod) }
    ).mapN { case ((backingCLient, receptionTimes), rateLimits) =>
      WSClient(backingCLient, rateLimits) -> receptionTimes
    }

    val scenario = for {
      (clientUnderTest, receptionTimesRef) <- clientUnderTestAndReceptionTimes
      wsConnect = clientUnderTest
        .wsConnect[Unit]("foo://example.com")
        .recover { case _: circe.DecodingFailure =>
          ()
        }
        .take(1)
      _ <- wsConnect.compile.drain.parReplicateA_(rateLimitPermits * 2 + 1)
      _ <- IO.sleep(rateLimitPeriod * (rateLimitPermits * 2 + 2))
      receptionTimes <- receptionTimesRef.get
    } yield receptionTimes

    TestControl.executeEmbed(scenario).flatMap { receptionTimes =>
      IO.print(receptionTimes) *>
        expect(true).pure
    }
  }

  // test("cancels while blocked on permit acquisition")

  // test("cancels stream mid consumption")

  // val program = new WSCLient.WSCLientLive()
}

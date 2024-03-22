package client

import weaver.SimpleIOSuite
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import org.http4s
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import scala.concurrent.duration.*
import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import cats.effect.std.Semaphore
import client.rateLimits.RLSemaphoreAndReleaseTime
import org.http4s.MalformedMessageBodyFailure
import cats.effect.testkit.TestControl

object HttpClientSpec extends SimpleIOSuite {

  def stubHttp4sClientAndRequestsRef =
    IO.ref(List.empty[FiniteDuration]).map { ref =>
      http4s.client.Client.apply[IO] { _request =>
        Resource.make[IO, http4s.Response[IO]](
          IO.realTime.flatMap { currentTime =>
              ref.update { receiveTimes => receiveTimes.prepended(currentTime) }
            }.as(http4s.Response.apply())
        )(_ => IO.unit)
      } -> ref
    }

  test("HttpClientLive respects rate limits") {
    val rateLimitPermits = 10
    val permitCostPerRequest = 1
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time

    def requestEmissionRateInLineWithRLimits(requestReceptionTimesInChronOrder: List[FiniteDuration]): Boolean = {
      val numberOfRequestsPerPeriod = requestReceptionTimesInChronOrder
        .foldLeft((Vector.empty[FiniteDuration], Vector.empty[Int])) { case ((timesInCurrentPeriod, acc), nextTime) =>
          val timesInNextPeriod = timesInCurrentPeriod.appended(nextTime).dropWhile(_ + rateLimitPeriod <= nextTime)

          timesInNextPeriod -> acc.appended(timesInNextPeriod.size)
        }._2

      // println(numberOfRequestsPerPeriod)
      numberOfRequestsPerPeriod.forall(_ <= rateLimitPermits)
    }

    TestControl
      .executeEmbed(
        (stubHttp4sClientAndRequestsRef, Semaphore(rateLimitPermits))
          .flatMapN { case ((backingClient, ref), sem) =>
            val clientUnderTest = HttpClient.HttpClientLive(httpClient = backingClient, RLSemaphoreAndReleaseTime(sem, rateLimitPeriod))
            clientUnderTest
              .get[Unit]("foo://example.com", permitCostPerRequest)
              .recover { case _: MalformedMessageBodyFailure =>
                ()
              }
              .replicateA_(rateLimitPermits * 2 + 1) *> ref.get
          }
      )
      .map { requestReceptionTimesInReverseChronOrder =>
        expect(requestEmissionRateInLineWithRLimits(requestReceptionTimesInReverseChronOrder.reverse))
      }
  }

  test("cancelling HttpClient#get while it is acquiring semaphore permits works") {
    val rateLimitPermits = 1
    val permitCostPerRequest = 1
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time

    val program = (stubHttp4sClientAndRequestsRef, Semaphore(rateLimitPermits))
      .flatMapN { case ((backingClient, ref), sem) =>
        val clientUnderTest = HttpClient.HttpClientLive(httpClient = backingClient, RLSemaphoreAndReleaseTime(sem, rateLimitPeriod))
        val sendRequest = clientUnderTest
          .get[Unit]("foo://example.com", permitCostPerRequest)
          .recover { case _: MalformedMessageBodyFailure =>
            ()
          }

        for {
          _ <- sendRequest
          fib2 <- Async[IO].start(sendRequest)
          fib3 <- Async[IO].start(sendRequest)
          _ <- IO.sleep(
            rateLimitPeriod / 2
          ) // to make sure fib2 can progress and block on permit acquisition before we cancel it on the next step
          _ <- fib2.cancel
          out2 <- fib2.join
          out3 <- fib3.join
          _ <- IO.sleep(
            rateLimitPeriod * 2
          ) // doubled to ensure the following action happens strictly after fib3 releases the permit it acquired
          permitsAvailableInTheEnd <- sem.available
        } yield (out2, out3, permitsAvailableInTheEnd)
      }

    TestControl.executeEmbed(program).map { case (out2, out3, permitsAvailableInTheEnd) =>
      expect(out2.isCanceled && out3.isSuccess && permitsAvailableInTheEnd == 1)
    }

  }

}

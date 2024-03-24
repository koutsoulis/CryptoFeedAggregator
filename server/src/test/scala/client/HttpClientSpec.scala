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
import client.testUtils.*
import client.HttpClient.HttpClientLive

object HttpClientSpec extends SimpleIOSuite {

  /**
   * Stub implementation of a http4s Http Client along with a mutable reference which holds request reception times Plays the role of the
   * server to the client-under-test and updates the Ref with the request reception times as it receives them from the client-under-test
   */
  val stubBackingClientAndRequestEmissionTimes: IO[(http4s.client.Client[IO], Ref[IO, Vector[FiniteDuration]])] =
    IO.ref(Vector.empty[FiniteDuration]).map { ref =>
      http4s.client.Client.apply[IO] { _request =>
        Resource.make[IO, http4s.Response[IO]](
          IO.realTime.flatMap { currentTime =>
              ref.update { receiveTimes => receiveTimes.appended(currentTime) }
            }.as(http4s.Response.apply())
        )(_ => IO.unit)
      } -> ref
    }

  def clientUnderTestAndRequestEmissionTimes(
      rateLimitPermits: Int,
      rateLimitPeriod: FiniteDuration): IO[(HttpClientLive[IO], Ref[IO, Vector[FiniteDuration]])] =
    (stubBackingClientAndRequestEmissionTimes, Semaphore(rateLimitPermits))
      .mapN { case ((backingClient, ref), sem) =>
        HttpClient.HttpClientLive(httpClient = backingClient, RLSemaphoreAndReleaseTime(sem, rateLimitPeriod)) -> ref
      }

  /**
   * We build scenarios we want to test in terms of this. Stubs out anything not relevant to the behaviour we're testing.
   */
  def wrappedGet(client: HttpClient[IO], permitCostPerRequest: Int): IO[Unit] = client
    .get[Unit]("foo://example.com", permitCostPerRequest)
    .recover { case _: MalformedMessageBodyFailure =>
      ()
    }

  {
    val rateLimitPermits = 10
    val permitCostPerRequest = 3
    val maxRequestsPerPeriodExpected = rateLimitPermits / permitCostPerRequest
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time
    val totalNumOfRequests = maxRequestsPerPeriodExpected * 2 + 1

    val scenario = for {
      (clientUnderTest, requestEmissionTimes) <- clientUnderTestAndRequestEmissionTimes(rateLimitPermits, rateLimitPeriod)
      sendRequest = wrappedGet(clientUnderTest, permitCostPerRequest)
      _ <- sendRequest.parReplicateA_(totalNumOfRequests)
      _ <- IO.sleep(
        1.millisecond
      ) // sleep for arbitrary positive duration to ensure the following action is strictly after permit acquisition
      requestEmissionTimes <- requestEmissionTimes.get
    } yield requestEmissionTimes

    test("HttpClientLive respects rate limits") {
      TestControl
        .executeEmbed(scenario)
        .map { requestReceptionTimes =>
          expect(requestEmissionRateInLineWithRLimits(requestReceptionTimes, maxRequestsPerPeriodExpected, rateLimitPeriod))
        }
    }

    test("HttpClientLive allows bursts in request traffic ") {
      TestControl.executeEmbed(scenario).map { requestEmissionTimes =>
        val rateLimitRefreshWindowsRequiredToGoThroughAllRequests =
          math.ceil(totalNumOfRequests.toDouble / maxRequestsPerPeriodExpected.toDouble).toInt

        // we send requests in batches, once per window
        expect(
          requestEmissionTimes.distinct.size == rateLimitRefreshWindowsRequiredToGoThroughAllRequests
        )
      }
    }

    test("HttpClientLive makes the most of the rate limits") {
      TestControl.executeEmbed(scenario).map { requestEmissionTimes =>
        expect(
          requestEmissionTimes.size == totalNumOfRequests
        )
      }
    }
  }

  test("cancelling HttpClient#get while it is acquiring semaphore permits works") {
    val rateLimitPermits = 1
    val permitCostPerRequest = 1
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time

    /**
     * send a request to exhaust the sole permit available; send a second and third one and wait for them to block; cancel the second;
     * progress time till evaluation; verify that the second request was cancelled, that the third went through and that there is exactly 1
     * permit available after all is done
     */
    val scenario = (stubBackingClientAndRequestEmissionTimes, Semaphore(rateLimitPermits))
      .flatMapN { case ((backingClient, ref), sem) =>
        val clientUnderTest = HttpClient.HttpClientLive(httpClient = backingClient, RLSemaphoreAndReleaseTime(sem, rateLimitPeriod))
        val sendRequest = wrappedGet(clientUnderTest, permitCostPerRequest)

        for {
          _fib1 <-
            sendRequest // not wrapped in GenSpawn#start to make sure _fib1 progresses and acquires the sole permit available before we spawn fib2 and fib3
          // we don't progress time to ensure the permit is not released
          fib2 <- Async[IO].start(sendRequest)
          fib3 <- Async[IO].start(sendRequest)
          _ <- IO.sleep(
            rateLimitPeriod / 4
          ) // to make sure fib2 can progress and block on permit acquisition before we cancel it on the next step
          _ <- fib2.cancel
          out2 <- fib2.join
          out3 <- fib3.join
          _ <- IO.sleep(
            rateLimitPeriod * 3
          ) // tripled to ensure the following action happens strictly after fib3 releases the permit it acquired
          // and also guard against potential regression wherein fib2 `releases` a permit even though we cancelled it before it acquired one
          permitsAvailableInTheEnd <- sem.available
        } yield (out2, out3, permitsAvailableInTheEnd)
      }

    TestControl.executeEmbed(scenario).map { case (out2, out3, permitsAvailableInTheEnd) =>
      expect(out2.isCanceled && out3.isSuccess && permitsAvailableInTheEnd == 1)
    }

  }

}

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
import client.testUtils.*
import scala.concurrent.duration.FiniteDuration
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.Logger

object RateLimitedWSClientSpec extends SimpleIOSuite {
  implicit val loggerFactory: Slf4jFactory[IO] = Slf4jFactory.create[IO]
  given Logger[IO] = Slf4jFactory.getLogger[IO]

  def stubBackingClient(
      delayBetweenElementsInTheStream: FiniteDuration =
        100.milliseconds, // arbitrary positive, no relation to other fixed numbers in the file
      frameLoad: Option[String] = None
  ): IO[(WSClientHighLevel[IO], Ref[IO, Vector[FiniteDuration]])] =
    IO.ref(Vector.empty[FiniteDuration]).map { receptionTimes =>
      val backingClient = new websocket.WSClientHighLevel[IO] {

        override def connectHighLevel(request: WSRequest): Resource[IO, WSConnectionHighLevel[IO]] =
          Resource.make(
            acquire = IO.pure(
              new WSConnectionHighLevel[IO] {

                override def receiveStream: Stream[IO, WSDataFrame] = Stream
                  .fromIterator[IO](
                    frameLoad
                      .map(Iterator.continually).getOrElse(Iterator.from(0))
                      .map(_.toString()).map { str => WSFrame.Text(str) },
                    1
                  ).spaced(delayBetweenElementsInTheStream)

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

  {
    val rateLimitPermits = 3
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time
    val maxRequestsPerPeriodExpected = rateLimitPermits
    val totalNumOfRequests = maxRequestsPerPeriodExpected * 2 + 1

    def clientUnderTestAndReceptionTimes = (
      stubBackingClient(),
      Semaphore[IO](rateLimitPermits).map { sem => RLSemaphoreAndReleaseTime(sem, rateLimitPeriod) }
    ).mapN { case ((backingCLient, receptionTimes), rateLimits) =>
      RateLimitedWSClient(backingCLient, rateLimits) -> receptionTimes
    }

    val scenario = for {
      (clientUnderTest, receptionTimesRef) <- clientUnderTestAndReceptionTimes
      wsConnect = clientUnderTest
        .wsConnect[Unit](uri"foo://example.com")
        .recover { case _: circe.DecodingFailure =>
          ()
        }
        .take(1)
      _ <- wsConnect.compile.drain.parReplicateA_(totalNumOfRequests)
      receptionTimes <- receptionTimesRef.get
    } yield receptionTimes

    test("respects rate limits") {
      TestControl.executeEmbed(scenario).map { receptionTimes =>
        expect(requestEmissionRateInLineWithRLimits(receptionTimes, maxRequestsPerPeriodExpected, rateLimitPeriod))
      }
    }

    test("allows bursts in request traffic") {
      TestControl.executeEmbed(scenario).map { requestEmissionTimes =>
        val rateLimitRefreshWindowsRequiredToGoThroughAllRequests =
          math.ceil(totalNumOfRequests.toDouble / maxRequestsPerPeriodExpected.toDouble).toInt

        // we send requests in batches, once per window
        expect(
          requestEmissionTimes.distinct.size == rateLimitRefreshWindowsRequiredToGoThroughAllRequests
        )
      }
    }

    test("makes the most of the rate limits") {
      TestControl.executeEmbed(scenario).map { requestEmissionTimes =>
        // send all of them
        expect(requestEmissionTimes.size == totalNumOfRequests) &&
        // send them at the earliest opportunity there are permits available
        expect(requestEmissionTimes.head == 0.seconds) &&
        expect(requestEmissionTimes.distinct.sliding2.forall { timesPair => timesPair._2 - timesPair._1 == rateLimitPeriod })
      }
    }
  }

  {
    val rateLimitPermits = 1
    val rateLimitPeriod = 100.milliseconds // arbitrary positive time
    val delayBetweenElementsInTheStream = 100.milliseconds

    def clientUnderTestAndRLSemaphore = (
      stubBackingClient(delayBetweenElementsInTheStream),
      Semaphore[IO](rateLimitPermits).map { sem => RLSemaphoreAndReleaseTime(sem, rateLimitPeriod) -> sem }
    ).mapN { case ((backingCLient, _), (rateLimits, sem)) =>
      RateLimitedWSClient(backingCLient, rateLimits) -> sem
    }

    test("cancels while blocked on permit acquisition") {
      val scenario = for {
        (clientUnderTest, sem) <- clientUnderTestAndRLSemaphore
        wsConnect = clientUnderTest
          .wsConnect[Int](uri"foo://example.com")
          .take(10)
        _connectionWhichAcquiresSolePermit <- GenSpawn[IO].start(wsConnect.compile.drain)
        _ <- IO.sleep(rateLimitPeriod / 2) // ensures _connectionWhichAcquiresSolePermit acquires permit but has not yet released it
        latestNumberProducedByCanceleeRef <- IO.ref(-1)
        permitsAvailableWhenCanceleeBlocksOnAcquisition <- sem.available
        connectionWhichBlocksOnPermitAcquisition <- GenSpawn[IO].start(
          wsConnect.evalTap(latestNumberProducedByCanceleeRef.set).compile.drain)
        _ <- connectionWhichBlocksOnPermitAcquisition.cancel
        _ <- _connectionWhichAcquiresSolePermit.join
        outcome <- connectionWhichBlocksOnPermitAcquisition.join
        latestNumberProducedByCancelee <- latestNumberProducedByCanceleeRef.get
        permitsAvailableEventually <- IO.sleep(rateLimitPeriod * 2) *> sem.available
      } yield (outcome, permitsAvailableWhenCanceleeBlocksOnAcquisition, latestNumberProducedByCancelee, permitsAvailableEventually)

      TestControl.executeEmbed(scenario).map {
        case (
              outcome,
              permitsAvailableWhenCanceleeBlocksOnAcquisition,
              latestNumberProducedByCancelee,
              permitsAvailableEventually
            ) =>
          expect(outcome.isCanceled) &&
          expect(latestNumberProducedByCancelee == -1) && // did not get past the resource acquisition stage
          expect(permitsAvailableWhenCanceleeBlocksOnAcquisition == 0) &&
          expect(permitsAvailableEventually == 1)
      }
    }

    test("cancels stream mid-consumption") {
      val scenario = for {
        (clientUnderTest, sem) <- clientUnderTestAndRLSemaphore
        wsConnect = clientUnderTest
          .wsConnect[Int](uri"foo://example.com")
          .take(10)
        latestNumberProducedByCanceleeRef <- IO.ref(-1)
        handleToStream <- GenSpawn[IO].start(wsConnect.evalTap(latestNumberProducedByCanceleeRef.set).compile.drain)
        _ <- IO.sleep(
          delayBetweenElementsInTheStream * 5 + delayBetweenElementsInTheStream / 2
        ) // wait for 5 elements to be produced before cancelling (and a half to avoid race conditions)
        _ <- handleToStream.cancel
        outcome <- handleToStream.join
        latestNumberProducedByCancelee <- latestNumberProducedByCanceleeRef.get
        _ <- IO.sleep(
          rateLimitPeriod
        ) // to ensure the permit is released, just to clarify, redundant since we already sleeped for longer earlier
        permitsAvailableInTheEnd <- sem.available
      } yield (outcome, latestNumberProducedByCancelee, permitsAvailableInTheEnd)

      TestControl.executeEmbed(scenario).map { case (outcome, latestNumberProducedByCancelee, permitsAvailableInTheEnd) =>
        expect(outcome.isCanceled) &&
        expect(latestNumberProducedByCancelee == 5) &&
        expect(permitsAvailableInTheEnd == 1)
      }
    }
  }

  test("permit release time depends only on the time of the resource acquisition stage of the Stream") {
    val rateLimitPermits = 1
    val rateLimitPeriod = 10.milliseconds // note it's much shorter than `delayBetweenElementsInTheStream`
    val delayBetweenElementsInTheStream = 10000.milliseconds

    def clientUnderTestAndRLSemaphore = (
      stubBackingClient(delayBetweenElementsInTheStream),
      Semaphore[IO](rateLimitPermits).map { sem => RLSemaphoreAndReleaseTime(sem, rateLimitPeriod) -> sem }
    ).mapN { case ((backingCLient, _), (rateLimits, sem)) =>
      RateLimitedWSClient(backingCLient, rateLimits) -> sem
    }

    val scenario = for {
      (clientUnderTest, sem) <- clientUnderTestAndRLSemaphore
      wsConnect = clientUnderTest
        .wsConnect[Int](uri"foo://example.com")
        .take(10)
      _streamHandle <- GenSpawn[IO].start(wsConnect.take(10).compile.drain)
      permitsAfterConnectAndBeforeRateLimitRefreshWindow <- IO.sleep(rateLimitPeriod / 2) *> sem.available
      permitsAfterRateLimitRefreshWindow <- IO.sleep(rateLimitPeriod) *> sem.available
      permitsAfterStreamConclusion <- _streamHandle.join *> IO.sleep(rateLimitPeriod) *> sem.available
    } yield (permitsAfterConnectAndBeforeRateLimitRefreshWindow, permitsAfterRateLimitRefreshWindow, permitsAfterStreamConclusion)

    TestControl.executeEmbed(scenario).map {
      case (permitsAfterConnectAndBeforeRateLimitRefreshWindow, permitsAfterRateLimitRefreshWindow, permitsAfterStreamConclusion) =>
        expect(permitsAfterConnectAndBeforeRateLimitRefreshWindow == 0) &&
        expect(permitsAfterRateLimitRefreshWindow == 1) &&
        expect(permitsAfterStreamConclusion == 1)
    }
  }

  test("raises sensible error if DTO regressed") {
    val rateLimitPermits = 1 // arbitrary
    val rateLimitPeriod = 0.milliseconds // arbitrary

    final case class DTOWhichRegressed(
        oldFieldName: String
    ) derives circe.Decoder

    val frameLoadWithNewIncompatibleStructure = """
      {"newFieldName":"whatever"}
    """

    def clientUnderTestAndRLSemaphore = (
      stubBackingClient(frameLoad = Some(frameLoadWithNewIncompatibleStructure)),
      Semaphore[IO](rateLimitPermits).map { sem => RLSemaphoreAndReleaseTime(sem, rateLimitPeriod) -> sem }
    ).mapN { case ((backingCLient, _), (rateLimits, sem)) =>
      RateLimitedWSClient(backingCLient, rateLimits) -> sem
    }

    val scenario = for {
      (clientUnderTest, _) <- clientUnderTestAndRLSemaphore
      _ <- clientUnderTest
        .wsConnect[DTOWhichRegressed](uri"foo://example.com")
        .take(1).compile.drain
    } yield ()

    TestControl.executeEmbed(scenario).attempt.map { outcome =>
      println(outcome)
      expect(outcome.isLeft)
    }
  }

}

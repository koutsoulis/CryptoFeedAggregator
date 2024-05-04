package client

import scala.concurrent.duration.FiniteDuration

object testUtils {
  def requestEmissionRateInLineWithRLimits(
      requestReceptionTimesInChronOrder: Vector[FiniteDuration],
      maxRequestsPerPeriodExpected: Int,
      rateLimitPeriod: FiniteDuration
  ): Boolean = {
    val numberOfRequestsPerPeriod = requestReceptionTimesInChronOrder
      .foldLeft((Vector.empty[FiniteDuration], Vector.empty[Int])) { case ((timesInCurrentPeriod, acc), nextTime) =>
        // dropping on equality does not reflect reality, but we use TestControl, time does not progress outside IO.sleep
        val timesInNextPeriod = timesInCurrentPeriod.appended(nextTime).dropWhile(_ + rateLimitPeriod <= nextTime)

        timesInNextPeriod -> acc.appended(timesInNextPeriod.size)
      }._2

    numberOfRequestsPerPeriod.forall(_ <= maxRequestsPerPeriodExpected)
  }
}

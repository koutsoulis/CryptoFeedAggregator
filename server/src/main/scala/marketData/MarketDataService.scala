package marketData

import cats.*
import cats.effect.*
import cats.effect.std.MapRef
import cats.effect.std.Mutex
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Signal
import marketData.exchange.Exchange
import marketData.names.Currency
import marketData.names.TradePair
import myMetrics.MyMetrics
import myMetrics.MyMetrics.IncomingConcurrentStreamsGauge
import scala.concurrent.duration.DurationInt

import scala.collection.concurrent.TrieMap

import names.FeedName

trait MarketDataService[F[_]] {
  def stream(feedName: FeedName[?]): Stream[F, feedName.Message]
  def activeCurrencyPairs: F[List[TradePair]]
}

object MarketDataService {

  /**
   * Responsible for managing the lifecycle of the backing feeds. Avoids over-provisioning when multiple users request the same feed.
   */
  def apply[F[_]](
      exchange: Exchange[F],
      incomingConcurrentStreamsGauge: IncomingConcurrentStreamsGauge[F]
  )(using F: Async[F]): Resource[F, MarketDataService[F]] = {

    /**
     * Triple containing what we need to share a data feed among multiple subscribers.
     *
     * @param signal
     *   Reports the updates of the backing data feed
     * @param finalizer
     *   Finalizer to close the backing data feed when subscribersCount drops to 0
     * @param subscribersCount
     *   Number of users currently sharing the backing data feed
     */
    case class SignalFinalizerCount[M](
        signal: Signal[F, Either[Throwable, M]],
        finalizer: F[Unit],
        subscribersCount: Int
    )

    val locks: F[Map[FeedName[?], Mutex[F]]] = exchange
      .allFeedNames.traverse { feedDef =>
        Mutex.apply[F].map(feedDef -> _)
      }.map(_.toMap)

    val initSFCs: F[Map[FeedName[?], Ref[F, Option[SignalFinalizerCount[?]]]]] = exchange
      .allFeedNames.traverse { feedDef =>
        Ref.of[F, Option[SignalFinalizerCount[?]]](None).map(feedDef -> _)
      }.map(_.toMap)

    val activeSubs: F[MapRef[F, FeedName[?], Option[Int]]] =
      F.delay {
        TrieMap
          .from[FeedName[?], Int](
            exchange.allFeedNames.map { _ -> 0 }
          )
      }.map(MapRef.fromScalaConcurrentMap)

    val activeCurrencyPairsSignalRes: Resource[F, Signal[F, List[TradePair]]] = // caches and refreshes every 2 minutes
      Stream
        .repeatEval[F, List[TradePair]](exchange.activeCurrencyPairs).meteredStartImmediately(2.minutes).hold1.compile.resource.onlyOrError

    val allCurrencyPairsAsSet = exchange.allCurrencyPairs.toSet

    (Resource.eval(locks), Resource.eval(initSFCs), activeCurrencyPairsSignalRes).mapN {
      case (
            locks,
            sfcMap,
            activeCurrencyPairsSignal
          ) =>
        def sfc[M](feedDef: FeedName[M]): F[Ref[F, Option[SignalFinalizerCount[M]]]] = {
          F.fromOption(sfcMap.get(feedDef).map(_.asInstanceOf[Ref[F, Option[SignalFinalizerCount[M]]]]), new Exception(""))
        }

        new MarketDataService[F] {

          // TODO: expose Signal instead of Stream to enable testing (move Signal -> Stream logic downstream)
          override def stream(feed: FeedName[?]): Stream[F, feed.Message] = {
            def listenToAndPotentiallySetupBackingFeed = (poll: Poll[F]) =>
              locks(feed).lock.surround {
                for {
                  sfcRef <- sfc(feed)
                  currentSFC <- sfcRef.get
                  updatesFromBackingFeed <- currentSFC match {
                    case None =>
                      // setup shared backing feed and register its signal and finalizer
                      poll(
                        backingStreamWrappedInPrometheusMetric(feed)
                          .attempt // attempt & rethrow later to ensure hold1Resource does not swallow the cause
                          .hold1Resource
                          .allocated
                      )
                        .flatTap { case (signal, finalizer) =>
                          sfcRef.set(Some(SignalFinalizerCount(signal, finalizer, 1)))
                        }.map(_._1)
                    case Some(SignalFinalizerCount(signal, finalizer, count)) =>
                      sfcRef.set(Some(SignalFinalizerCount(signal, finalizer, count + 1))).as(signal)
                  }
                } yield updatesFromBackingFeed.discrete.rethrow
              }

              def potentiallyShutdownBackingFeed = locks(feed).lock.surround {
                for {
                  sfcRef <- sfc(feed)
                  SignalFinalizerCount(signal, finalizer, count) <- sfcRef
                    .get.map(
                      _.toRight(new Exception("SignalFinalizerCount Map was expected to contain an entry at time of release"))).rethrow
                  _ <-
                    if (count == 1) {
                      finalizer *> sfcRef.set(None)
                    } else {
                      sfcRef.set(Some(SignalFinalizerCount(signal, finalizer, count - 1)))
                    }
                } yield ()
              }

              Stream
                .bracketFull(
                  acquire = listenToAndPotentiallySetupBackingFeed
                )(
                  release = (_, _) => potentiallyShutdownBackingFeed
                ).flatten
          }

          override def activeCurrencyPairs: F[List[TradePair]] =
            activeCurrencyPairsSignal
              .get
              .map(_.filter(allCurrencyPairsAsSet.contains)) // drop any tradepairs listed on the exchange after the server's initialization

          private def backingStreamWrappedInPrometheusMetric(feed: FeedName[?]): Stream[F, feed.Message] = Stream.bracket(
            incomingConcurrentStreamsGauge.value.inc(exchange.name -> feed)
          )(_ => incomingConcurrentStreamsGauge.value.dec(exchange.name -> feed)) >> exchange.stream(feed)
        }
    }

  }

  def stub[F[_]](using Async[F])(
      streamStub: (feedName: FeedName[?]) => Stream[F, feedName.Message] = { _ => Stream.raiseError(new UnsupportedOperationException) },
      activeCurrencyPairsStub: F[List[TradePair]] = List(TradePair(base = Currency("BTC"), quote = Currency("ETH"))).pure[F]
  ) = new MarketDataService[F] {

    override def stream(feed: FeedName[?]): Stream[F, feed.Message] = streamStub(feed)

    override def activeCurrencyPairs: F[List[TradePair]] = activeCurrencyPairsStub

  }
}

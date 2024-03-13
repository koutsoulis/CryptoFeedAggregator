package marketData

import fs2.Stream
import fs2.concurrent.Topic

import cats.*
import cats.data.*
import cats.syntax.all.*

import cats.effect.*
import cats.effect.std.MapRef
import cats.effect.std.Mutex
import cats.effect.std.AtomicCell

import service.*
import marketData.exchange.ExchangeParameters
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import marketData.FeedDefinition.Level2
import marketData.FeedDefinition.Stub
import scala.collection.concurrent.TrieMap

trait MarketDataService[F[_]] {
  def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message]
}

object MarketDataService {

  def apply[F[_]](backingStreams: BackingStreams[F], exchangeParameters: ExchangeParameters)(using F: Async[F]): F[MarketDataService[F]] = {
    type SubscribersCount = Int
    val locks: F[Map[FeedDefinition[?], Mutex[F]]] = exchangeParameters
      .allFeedDefs.traverse { feedDef =>
        Mutex.apply[F].map(feedDef -> _)
      }.map(_.toMap)

    val topics: F[MapRef[F, FeedDefinition[?], Option[Topic[F, ?]]]] = exchangeParameters
      .allFeedDefs.traverse {
        case feed @ Level2(currency1, currency2) => Topic[F, Level2.Message].map(feed -> _)
        case feed: Stub => Topic[F, Stub.Message].map(feed -> _)
      }.map(TrieMap.from[FeedDefinition[?], Topic[F, ?]]).map(MapRef.fromScalaConcurrentMap)

    val activeSubs: F[MapRef[F, FeedDefinition[?], Option[SubscribersCount]]] =
      F.delay {
        TrieMap
          .from[FeedDefinition[?], SubscribersCount](
            exchangeParameters.allFeedDefs.map { _ -> 0 }
          )
      }.map(MapRef.fromScalaConcurrentMap)

    (locks, topics, activeSubs).mapN {
      case (
            locks,
            topics,
            activeSubs
          ) =>
        new MarketDataService[F] {

          override def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message] = {
            def setupConsumingStream = locks(feed).lock.surround {
              val registerPublisherIfFirstToSubscribe =
                activeSubs(feed).get.flatMap { subsCount =>
                  Topic
                    .apply[F, feed.Message]
                    .flatTap { topic => topics(feed).set(Some(topic)) }
                    .flatTap { topic =>
                      F.background {
                        backingStreams.stream(feed).through(topic.publish).compile.drain
                      }.allocated
                    }
                    .whenA(subsCount.contains(0))
                }

              val incrementSubsCount =
                activeSubs(feed).tryModify { count => count.map(_ + 1) -> () }.map(_.toRight(new Exception)).flatMap(F.fromEither)
              val subscribeToTopic: F[Stream[F, Message]] =
                topics(feed)
                  .get.map { case someTopic: Some[Topic[F, feed.Message] @unchecked] => someTopic.value.subscribeUnbounded }

              registerPublisherIfFirstToSubscribe *> incrementSubsCount *> subscribeToTopic
            }

            val decrementSubsCountAndPotentiallyStopPublisher = locks(feed).lock.surround {
              val deregisterPublisherIfLastToUnsub =
                (activeSubs(feed).get, topics(feed).get).flatMapN { case (subsCount, topic: Some[Topic[F, feed.Message] @unchecked]) =>
                  if (subsCount.contains(1)) topic.value.close.void
                  else F.unit
                }

              val decrementSubsCount = activeSubs(feed).modify { count => count.map(_ - 1) -> () }

              deregisterPublisherIfLastToUnsub *> decrementSubsCount
            }

            Stream
              .bracket(
                acquire = setupConsumingStream
              )(
                release = _ => decrementSubsCountAndPotentiallyStopPublisher
              ).flatten
          }
        }
    }

  }
}

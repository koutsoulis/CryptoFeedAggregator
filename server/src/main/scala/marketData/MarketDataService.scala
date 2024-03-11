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

trait MarketDataService[F[_]] {
  def stream(feed: FeedDefinition[?]): Stream[F, feed.Message]
}

object MarketDataService {

  def apply[F[_]: Sync](backingStreams: BackingStreams[F], exchangeParameters: ExchangeParameters): MarketDataService[F] = {
    type SubscribersCount = Int
    val locks: Map[FeedDefinition[?], Mutex[F]] =
      ??? // populate at service instantiation for all feedDefs under exchangeParameters; same for below

    val topics: MapRef[F, FeedDefinition[?], Topic[F, ?]] = ???

    val activeSubs: MapRef[F, FeedDefinition[?], SubscribersCount] = ???

    new MarketDataService[F] {

      override def stream(feed: FeedDefinition[?]): Stream[F, feed.Message] = {
        def setupConsumingStream = locks(feed).lock.surround {
          val registerPublisherIfFirstToSubscribe =
            (activeSubs(feed).get, topics(feed).get).flatMapN { case (subsCount, topic: Topic[F, feed.Message]) =>
              if (subsCount == 0) backingStreams.stream(feed).through(topic.publish).compile.drain
              else Sync[F].unit
            }

          val incrementSubsCount = activeSubs(feed).modify(_ + 1 -> ())
          val subscribeToTopic = topics(feed).get.map { case (topic: Topic[F, feed.Message]) => topic.subscribeUnbounded }

          registerPublisherIfFirstToSubscribe *> incrementSubsCount *> subscribeToTopic
        }

        val decrementSubsCountAndPotentiallyStopPublisher = locks(feed).lock.surround {
          val deregisterPublisherIfLastToUnsub =
            (activeSubs(feed).get, topics(feed).get).flatMapN { case (subsCount, topic: Topic[F, feed.Message]) =>
              if (subsCount == 1) backingStreams.stream(feed).through(topic.publish).compile.drain
              else Sync[F].unit
            }

          val decrementSubsCount = activeSubs(feed).modify(_ - 1 -> ())

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

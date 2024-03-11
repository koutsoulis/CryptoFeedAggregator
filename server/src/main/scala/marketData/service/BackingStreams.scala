package marketData.service

import cats.effect.*
import fs2.Stream
import marketData.FeedDefinition

trait BackingStreams[F[_]] {
  def stream(feed: FeedDefinition[?]): Stream[F, feed.Message]
}

object BackingStreams {
  def stub[F[_]]: BackingStreams[F] = ???
}

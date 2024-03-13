package marketData.service

import cats.effect.*
import fs2.Stream
import marketData.FeedDefinition
import fs2.concurrent.SignallingRef
import fs2.concurrent.Signal

trait BackingStreams[F[_]] {
  def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message]
}

object BackingStreams {
  // def stub[F[_]](
  //     streamStub: FeedDefinition[?] => Stream[F, ?] = ???
  // ): BackingStreams[F] = new BackingStreams[F] {

  //   override def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message] = streamStub(feed)

  // }
}

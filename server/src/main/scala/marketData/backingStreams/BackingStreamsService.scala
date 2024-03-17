package marketData.backingStreams

import cats.effect.*
import fs2.Stream
import marketData.FeedDefinition
import fs2.concurrent.SignallingRef
import fs2.concurrent.Signal
import marketData.exchange.ExchangeSpecific
import org.http4s.jdkhttpclient.JdkWSClient
import org.http4s.client.websocket.WSClientHighLevel

trait BackingStreamsService[F[_]] {
  def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message]
}

object BackingStreamsService {
  class Live[F[_]](exchangeParameters: ExchangeSpecific, wsClient: WSClientHighLevel[F]) extends BackingStreamsService[F] {
    override def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message] = {
      // wsClient.connectHighLevel()
      ???
    }
  }
  // def stub[F[_]](
  //     streamStub: FeedDefinition[?] => Stream[F, ?] = ???
  // ): BackingStreams[F] = new BackingStreams[F] {

  //   override def stream[Message](feed: FeedDefinition[Message]): Stream[F, Message] = streamStub(feed)

  // }
}

package com.rockthejvm.jobsboard.components

import tyrian.CSS.*
import tyrian.*
import tyrian.Html.*

import cats.effect.*
import org.http4s
import org.http4s.client.websocket
import org.http4s.client.websocket.WSDataFrame
import org.http4s.client.websocket.WSFrame.Text
import org.http4s.client.websocket.WSFrame.Binary
import _root_.io.circe
import monocle.syntax.all.*
import fs2.Stream
import concurrent.duration.DurationInt
import _root_.io.bullet.borer.Cbor
import _root_.io.bullet.borer.compat.scodec.*
import marketData.exchange.impl.binance.domain.Orderbook
import org.http4s.client.websocket.WSFrame
import org.http4s.QueryParamEncoder
import marketData.names.FeedName
import marketData.names.Currency
import com.rockthejvm.jobsboard.components.OrderbookView
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectExchange
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectFeed
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectCurrency1
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.SelectCurrency2
import com.rockthejvm.jobsboard.components.MarketFeedSelectionStage.TotalSelection
import _root_.io.bullet.borer
import com.rockthejvm.jobsboard.App.Model
import com.rockthejvm.jobsboard.App.Msg
import names.ExchangeName

object StreamFromServer {
  def stream[M <: Msg](exchange: ExchangeName, feedName: FeedName[M]): Sub[IO, M] = {
    val undelyingStream: Stream[IO, M] = Stream
      .resource(
        http4s
          .dom.WebSocketClient[IO].connectHighLevel(
            websocket.WSRequest(
              uri = http4s
                .Uri.fromString(s"ws://typelevel-project-backend.kotopoulion.xyz:4041/${exchange.toString}") // wss for prod
                .map(_.withQueryParam("feedName", feedName: FeedName[?]))
                .getOrElse(None.get)
            )
          )
      )
      .flatMap { conn =>
        Stream.repeatEval(conn.send(WSFrame.Text("")) <* IO.sleep(500.millis)) `zipRight`
          conn
            .receiveStream
            .map {
              case Binary(data, _) => Cbor.decode(data).to[M](using feedName.borerDecoderForMessage).value
              case _ => throw new Exception("unexpected non binary ws frame")
            }
      }
    Sub.make(feedName.getClass.getSimpleName, undelyingStream)
  }
}

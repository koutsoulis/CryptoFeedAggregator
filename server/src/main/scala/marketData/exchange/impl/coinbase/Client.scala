package marketData.exchange.impl.coinbase

import org.http4s.client.websocket.WSClientHighLevel
import cats.effect.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import mouse.all.*
import fs2.{Stream, Pull}
import client.WSClient
import marketData.names.TradePair
import org.http4s.implicits.uri
import marketData.exchange.impl.coinbase.dto.Level2Message
import marketData.exchange.impl.coinbase.dto.Level2Message.Snapshot
import marketData.exchange.impl.coinbase.dto.Level2Message.L2Update
import _root_.io.scalaland.chimney.syntax.*
import _root_.io.scalaland.chimney.cats.*
import marketData.domain.Orderbook
import client.rateLimits.RLSemaphoreAndReleaseTime
import cats.effect.std.Semaphore
import org.typelevel.log4cats.Logger

class Client[F[_]] private (
    wsClient: WSClient[F]
)(using F: Async[F]) {
  private val baseURI = uri"http:/example.com/page"

  def orderbook(tradePair: TradePair): Stream[F, marketData.domain.Orderbook] = {
    val subscriptionMessage: String = ???

    wsClient
      .wsConnect[Level2Message](uri = baseURI.renderString, subscriptionMessage = Some(subscriptionMessage))
      .pull.uncons1.flatMap {
        case Some((headSnapshot @ Snapshot(_, _)) -> tail) =>
          val tailEnsuringAllAreUpdates: Stream[F, L2Update] = tail.evalMapChunk {
            case update: L2Update => F.pure(update)
            case _else => F.raiseError(new Exception(s"expected ${L2Update.toString}, got $_else"))
          }
          tailEnsuringAllAreUpdates
            .scan[Orderbook](headSnapshot.transformInto[Orderbook]) { case (snapshot, update) =>
              update.apply(snapshot)
            }.pull.echo
        case _ => Pull.raiseError(new Exception(s"Level2 incoming websocket stream should start with a Snapshot message"))
      }.stream
  }
}

object Client {
  def apply[F[_]: Async: Logger](wsClient: WSClientHighLevel[F]): F[Client[F]] = {
    Semaphore(constants.websocketRequestsPerSecondPerIP)
      .map { sem =>
        RLSemaphoreAndReleaseTime(semaphore = sem, releaseTime = constants.websocketRateLimitRefreshPeriod)
      }.map { wsEstablishConnectionRL =>
        WSClient
          .apply(
            wsClient = wsClient,
            wsEstablishConnectionRL = wsEstablishConnectionRL
          )
      }.map { wsClient => new Client(wsClient) }
  }
}

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object HmacSha256Signer {
  def createSignature(cbAccessTimestamp: String, method: String, requestPath: String, secret: String): String = {
    // Concatenate required parts to create the prehash string
    val message = s"$cbAccessTimestamp$method$requestPath"

    // Decode the Base64 secret to use as the key
    val secretDecodedBytes = Base64.getDecoder.decode(secret)
    val keySpec = new SecretKeySpec(secretDecodedBytes, "HmacSHA256")

    // Initialize the MAC with SHA256
    val hmacSha256 = Mac.getInstance("HmacSHA256")
    hmacSha256.init(keySpec)

    // Sign the message with the HMAC and Base64 encode the result
    val signatureBytes = hmacSha256.doFinal(message.getBytes("UTF-8"))
    val signatureBase64 = Base64.getEncoder.encodeToString(signatureBytes)

    signatureBase64
  }
}

// Example usage:
// object Main extends App {
//   val cbAccessTimestamp = "YOUR_TIMESTAMP"
//   val method = "POST" // GET, POST, DELETE, etc.
//   val requestPath = "/orders"
//   val body = "{\"price\": \"1.0\",\"size\": \"0.01\",\"side\": \"buy\",\"product_id\": \"BTC-USD\"}"
//   val secret = "YOUR_SECRET"

//   val signature = HmacSha256Signer.createSignature(cbAccessTimestamp, method, requestPath, body, secret)
//   println(s"Signature: $signature")
// }

package com.rockthejvm.jobsboard.modules.cryptoPriceProvider.cryptoCompare

import _root_.io.circe.Decoder
import _root_.io.circe.generic.semiauto.*
import cats.*
import cats.data.*
import cats.syntax.all.*
import cats.derived.strict.*

object dto {
  sealed trait Message
  object Message {
    implicit val decoder: Decoder[Message] =
      List[Decoder[Message]](
        Decoder[CryptoData].widen,
        Decoder[UnrecoverableError].widen,
        Decoder[Ignore.type].widen
      ).reduceLeft(_ or _)

    // {"TYPE":"5","MARKET":"CCCAGG","FROMSYMBOL":"BTC","TOSYMBOL":"USD","FLAGS":2,"MEDIAN":49735.6637659428,"LASTTRADEID":"217093517","PRICE":49735.6637659428,"LASTUPDATE":1707832427,"LASTVOLUME":0.02800179,"LASTVOLUMETO":1392.2750404647,"VOLUMEHOUR":2593.3459684,"VOLUMEHOURTO":128681184.345007,"VOLUMEDAY":15494.02153829,"VOLUMEDAYTO":773812157.387051,"VOLUME24HOUR":49236.99141083,"VOLUME24HOURTO":2449291847.091117,"CURRENTSUPPLYMKTCAP":976108705309.5936,"CIRCULATINGSUPPLYMKTCAP":976108705309.5936,"MAXSUPPLYMKTCAP":1044448937935.905}
    case class CryptoData(
        TYPE: Int,
        // MARKET: String,
        FROMSYMBOL: String,
        // TOSYMBOL: String,
        // FLAGS: Int,
        // MEDIAN: Double,
        // LASTTRADEID: String,
        PRICE: Double
        // LASTUPDATE: Long,
        // LASTVOLUME: Double,
        // LASTVOLUMETO: Double,
        // VOLUMEHOUR: Double,
        // VOLUMEHOURTO: Double,
        // VOLUMEDAY: Double,
        // VOLUMEDAYTO: Double,
        // VOLUME24HOUR: Double,
        // VOLUME24HOURTO: Double,
        // CURRENTSUPPLYMKTCAP: Double,
        // CIRCULATINGSUPPLYMKTCAP: Double,
        // MAXSUPPLYMKTCAP: Double
    ) extends Message
        derives Decoder,
          Eq

    object Ignore extends Message {
      implicit val decoder: Decoder[Ignore.type] = Decoder.const(Ignore)
    }

    /**
     * Represents any kind of message from cryptodata following which we should drop the websocket connection and do not
     * attempt to reconnect
     *
     * Wraps a single Contents case class which reflects the decoded JSON object's shape.
     *
     * @param contents
     */
    case class UnrecoverableError(
        contents: UnrecoverableError.Contents
    ) extends Message

    object UnrecoverableError {
      case class Contents(
          TYPE: Int,
          MESSAGE: String,
          INFO: String
      ) derives Decoder,
            Show

      implicit val decoder: Decoder[UnrecoverableError] =
        Decoder[Contents].emap { contents =>
          Either.cond(
            test = unrecoverablePredicate(contents),
            right = UnrecoverableError(contents),
            left = "not unrecoverable"
          )
        }

      def unrecoverablePredicate(contents: Contents): Boolean =
        contents.TYPE >= 400 && contents.TYPE < 600 && contents.MESSAGE != "FORCE_DISCONNECT"
    }
  }

}

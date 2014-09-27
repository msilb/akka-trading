package org.akkatrading.live

import java.time.ZonedDateTime

import akka.actor._
import akka.util.Timeout
import org.akkatrading.live.CandleFetcher._
import org.akkatrading.live.util.DateUtils._
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.encoding.{Deflate, Gzip}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object CandleFetcher {

  def props(hostConnector: ActorRef, orderManager: ActorRef): Props = Props(new CandleFetcher(hostConnector, orderManager))

  case class FetchCandles(instrument: String, count: Int, granularity: String, candleFormat: String)

  case class PriceUpdate(instrument: String, current: Candle, previous: Candle)

  case object NewInterval

  case class Candle(time: ZonedDateTime, openBid: Double, openAsk: Double, highBid: Double, highAsk: Double, lowBid: Double, lowAsk: Double, closeBid: Double, closeAsk: Double, volume: Int, complete: Boolean)

  case class CandleResponse(instrument: String, granularity: String, candles: List[Candle])

  object CandleJsonProtocol extends DefaultJsonProtocol {
    implicit val candleFmt = jsonFormat11(Candle)
    implicit val candleResponseFmt = jsonFormat3(CandleResponse)
  }

}

class CandleFetcher(connector: ActorRef, orderManager: ActorRef) extends Actor with ActorLogging with AuthInfo {

  import context.dispatcher
  import org.akkatrading.live.CandleFetcher.CandleJsonProtocol._

  implicit val timeout: Timeout = Timeout(15 seconds)

  context.system.scheduler.schedule(0 milliseconds, 500 milliseconds, self, FetchCandles("EUR_USD", 2, "M1", "bidask"))

  val pipeline: HttpRequest => Future[CandleResponse] = (
    addCredentials(OAuth2BearerToken(authToken))
      ~> encode(Gzip)
      ~> sendReceive(connector)
      ~> decode(Deflate)
      ~> unmarshal[CandleResponse]
    )

  var previousCandle: Candle = null

  def receive = {
    case FetchCandles(instrument, count, granularity, candleFormat) =>
      val response: Future[CandleResponse] =
        pipeline(Get(s"/v1/candles?instrument=$instrument&count=$count&candleFormat=$candleFormat&granularity=$granularity"))
      response onComplete {
        case Success(CandleResponse(_, _, candles)) =>
          log.debug("Fetched candles: {}", candles)
          val priceUpdate = PriceUpdate(instrument, candles(1), candles(0))
          orderManager ! priceUpdate
          if (candles(0) != previousCandle) {
            previousCandle = candles(0)
            orderManager ! NewInterval
          }

        case Success(somethingUnexpected) =>
          log.warning("The Oanda API call was successful but returned something unexpected: '{}'.", somethingUnexpected)

        case Failure(error) =>
          log.error(error, "Couldn't fetch candles")
      }
    case other => log.info("Received something unexpected: {}", other)
  }
}

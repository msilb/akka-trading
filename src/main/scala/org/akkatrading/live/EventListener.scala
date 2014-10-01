package org.akkatrading.live

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.akkatrading.live.EventListener._
import org.akkatrading.live.util.DateUtils._
import spray.http.{ContentTypes, HttpEntity, MessageChunk, OAuth2BearerToken}
import spray.httpx.RequestBuilding._
import spray.httpx.unmarshalling._
import spray.json._

import scala.concurrent.duration._

object EventListener {

  def props(hostConnector: ActorRef, orderManager: ActorRef): Props = Props(new EventListener(hostConnector, orderManager))

  case object SubscribeToEvents

  case class OrderFilled(side: String, spawnedTradeId: Int)

  case object OrderCanceled

  case object StopLossFilled

  case object TakeProfitFilled

  case class TradeOpened(id: Int, units: Int)

  case class TradeReduced(id: Int, units: Int, pl: Double, interest: Double)

  case class Transaction(id: Int,
                         accountId: Int,
                         time: ZonedDateTime,
                         `type`: String,
                         instrument: Option[String],
                         side: Option[String],
                         units: Option[Int],
                         price: Option[Double],
                         lowerBound: Option[Double],
                         upperBound: Option[Double],
                         takeProfitPrice: Option[Double],
                         stopLossPrice: Option[Double],
                         trailingStopLossDistance: Option[Double],
                         pl: Option[Double],
                         interest: Option[Double],
                         accountBalance: Option[Double],
                         tradeId: Option[Int],
                         orderId: Option[Int],
                         expiry: Option[ZonedDateTime],
                         reason: Option[String],
                         tradeOpened: Option[TradeOpened],
                         tradeReduced: Option[TradeReduced])

  case class AccountEvent(transaction: Transaction)

  case class Heartbeat(time: ZonedDateTime)

  case class HeartbeatResponse(heartbeat: Heartbeat)

  object EventJsonProtocol extends DefaultJsonProtocol {
    implicit val tradeOpenedFormat = jsonFormat2(TradeOpened)
    implicit val tradeReducedFormat = jsonFormat4(TradeReduced)
    implicit val transactionFormat = jsonFormat22(Transaction)
    implicit val accountEventFormat = jsonFormat1(AccountEvent)
    implicit val heartbeatFormat = jsonFormat1(Heartbeat)
    implicit val heartbeatResponseFormat = jsonFormat1(HeartbeatResponse)
  }

}

class EventListener(hostConnector: ActorRef, orderManager: ActorRef) extends Actor with ActorLogging with AuthInfo {

  implicit val timeout: Timeout = Timeout(15.seconds)

  import org.akkatrading.live.EventListener.EventJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  def receive = {
    case SubscribeToEvents =>
      hostConnector ! Get(s"/v1/events?accountIds=$accountId") ~> addCredentials(OAuth2BearerToken(authToken))
    case MessageChunk(data, _) =>
      data.asString.lines.foreach { line =>
        val entity = HttpEntity(ContentTypes.`application/json`, line)
        val transactionMaybe = entity.as[AccountEvent].fold(error => None, event => Some(event.transaction))
        transactionMaybe.map {
          case t if t.`type` == "ORDER_FILLED" => orderManager ! OrderFilled(t.side.get, t.tradeOpened.map(_.id).getOrElse(t.tradeReduced.get.id))
          case t if t.`type` == "ORDER_CANCEL" => orderManager ! OrderCanceled
          case t if t.`type` == "STOP_LOSS_FILLED" => orderManager ! StopLossFilled
          case t if t.`type` == "TAKE_PROFIT_FILLED" => orderManager ! TakeProfitFilled
          case _ => log.debug("Received some other transaction")
        }

      }
    case other =>
      log.info("Received {}", other)
  }
}

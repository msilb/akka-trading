package org.akkatrading.live

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.akkatrading.live.TradeModifier.{ModifyTradeConfirmation, ModifyTradeRequest}
import org.akkatrading.live.util.DateUtils._
import org.akkatrading.live.util.NumberUtils._
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TradeModifier {

  def props(connector: ActorRef): Props = Props(new TradeModifier(connector))

  case class ModifyTradeRequest(id: Int,
                                stopLoss: Option[Double] = None,
                                takeProfit: Option[Double] = None,
                                trailingStop: Option[Int] = None)

  case class ModifyTradeConfirmation(id: Int,
                                     instrument: String,
                                     units: Int,
                                     side: String,
                                     time: ZonedDateTime,
                                     price: Double,
                                     takeProfit: Option[Double],
                                     stopLoss: Option[Double],
                                     trailingStop: Option[Int],
                                     trailingAmount: Option[Double])

  object ModifyTradeJsonProtocol extends DefaultJsonProtocol {
    implicit val modifyTradeConfirmationFormat = jsonFormat10(ModifyTradeConfirmation)
  }

}

class TradeModifier(connector: ActorRef) extends Actor with ActorLogging with AuthInfo {

  import context.dispatcher
  import org.akkatrading.live.TradeModifier.ModifyTradeJsonProtocol._

  implicit val timeout = Timeout(5 seconds)

  val pipeline = addCredentials(OAuth2BearerToken(authToken)) ~> sendReceive(connector) ~> unmarshal[ModifyTradeConfirmation]

  override def receive = {
    case request: ModifyTradeRequest => handleRequest(sender(), request)
  }

  def handleRequest(sender: ActorRef, modifyTradeRequest: ModifyTradeRequest) = {
    val request =
      HttpRequest(
        method = HttpMethods.PATCH,
        uri = s"/v1/accounts/$accountId/trades/${modifyTradeRequest.id}",
        entity =
          HttpEntity(
            ContentType(MediaTypes.`application/x-www-form-urlencoded`),
            modifyTradeRequest.takeProfit.map(tp => s"takeProfit=${decimalFormatter.format(tp)}").getOrElse("")
          )
      )
    pipeline(request) onComplete {
      case Success(conf: ModifyTradeConfirmation) =>
        log.info("Trade modified: {}", conf)
        sender ! conf

      case Success(somethingUnexpected) =>
        log.warning("The Oanda API call was successful but returned something unexpected: '{}'.", somethingUnexpected)

      case Failure(error) =>
        log.error(error, "Couldn't modify trade")
    }
  }
}

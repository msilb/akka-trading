package org.akkatrading.live

import java.net.URLEncoder
import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.akkatrading.live.OrderCreator.{CreateOrderConfirmation, CreateOrderRequest}
import org.akkatrading.live.util.DateUtils._
import org.akkatrading.live.util.NumberUtils._
import spray.client.pipelining._
import spray.http._
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object OrderCreator {

  def props(connector: ActorRef): Props = Props(new OrderCreator(connector))

  case class CreateOrderRequest(instrument: String, units: Int, side: String, `type`: String, expiry: ZonedDateTime, price: Double, stopLoss: Option[Double], takeProfit: Option[Double])

  case class OrderOpened(id: Int,
                         units: Int,
                         side: String,
                         expiry: ZonedDateTime,
                         upperBound: Double,
                         lowerBound: Double,
                         takeProfit: Double,
                         stopLoss: Double,
                         trailingStop: Double)

  case class CreateOrderConfirmation(instrument: String, time: ZonedDateTime, price: Double, orderOpened: OrderOpened)

  object CreateOrderJsonProtocol extends DefaultJsonProtocol {
    implicit val orderOpenedFormat = jsonFormat9(OrderOpened)
    implicit val createOrderConfirmationFormat = jsonFormat4(CreateOrderConfirmation)
  }

}

class OrderCreator(connector: ActorRef) extends Actor with ActorLogging with AuthInfo {

  import context.dispatcher

  implicit val timeout = Timeout(5 seconds)

  import org.akkatrading.live.OrderCreator.CreateOrderJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  val orderCreatePipeline = (addCredentials(OAuth2BearerToken(authToken))
    ~> sendReceive(connector)
    ~> unmarshal[CreateOrderConfirmation]
    )

  override def receive = {
    case request: CreateOrderRequest => handleRequest(sender(), request)
  }

  def handleRequest(sender: ActorRef, orderRequest: CreateOrderRequest) = {
    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"/v1/accounts/$accountId/orders",
        entity =
          HttpEntity(
            ContentType(MediaTypes.`application/x-www-form-urlencoded`),
            s"instrument=${orderRequest.instrument}" +
              s"&units=${orderRequest.units}" +
              s"&side=${orderRequest.side}" +
              s"&type=${orderRequest.`type`}" +
              s"&expiry=${URLEncoder.encode(dateTimeFormatter.format(orderRequest.expiry), "UTF-8")}" +
              s"&price=${decimalFormatter.format(orderRequest.price)}" +
              orderRequest.stopLoss.map(sl => s"&stopLoss=${decimalFormatter.format(sl)}").getOrElse("") +
              orderRequest.takeProfit.map(tp => s"&takeProfit=${decimalFormatter.format(tp)}").getOrElse("")
          )
      )
    orderCreatePipeline(request) onComplete {
      case Success(conf: CreateOrderConfirmation) =>
        log.info("Limit Order opened: {}", conf)
        sender ! conf

      case Success(somethingUnexpected) =>
        log.warning("The Oanda API call was successful but returned something unexpected: '{}'.", somethingUnexpected)

      case Failure(error) =>
        log.error(error, "Couldn't place order")
    }
  }
}
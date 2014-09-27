package org.akkatrading.live

import akka.actor.{ActorRef, FSM, Props}
import org.akkatrading.live.StrategyFSM._

import scala.concurrent.duration._

object StrategyFSM {

  def props(connector: ActorRef): Props = Props(new StrategyFSM(connector))

  sealed trait State

  case object Flat extends State

  sealed trait Data

  case object Empty extends Data

}

class StrategyFSM(connector: ActorRef) extends FSM[State, Data] with AuthInfo {

  implicit val timeout: akka.util.Timeout = akka.util.Timeout(15 seconds)

  val orderCreator = context.actorOf(OrderCreator.props(connector), "orderCreator")
  val orderCanceler = context.actorOf(OrderCanceler.props(connector), "orderCanceler")
  val tradeModifier = context.actorOf(TradeModifier.props(connector), "tradeModifier")

  startWith(Flat, Empty)

  // TODO: implement your strategy here

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  initialize()
}

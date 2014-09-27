package org.akkatrading.backtest.strategy

import akka.actor.{Actor, FSM}
import org.akkatrading.backtest.strategy.StrategyFSM._

import scala.math.{log => ln}

object StrategyFSM {

  sealed trait State

  case object Flat extends State

  case object Long extends State

  case object Short extends State

  sealed trait Data

  case object Empty extends Data

}

class StrategyFSM extends Actor with FSM[State, Data] {

  startWith(Flat, Empty)

  whenUnhandled {
    case Event(e, s) =>
      log.warning("Received unhandled request {} in state {}/{}", e, stateName, s)
      stay()
  }

  initialize()
}

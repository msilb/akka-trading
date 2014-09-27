package org.akkatrading.backtest.model

case class LimitOrder(direction: Direction, level: Double, amount: Double, stopLossOrder: Option[LimitOrder] = None)

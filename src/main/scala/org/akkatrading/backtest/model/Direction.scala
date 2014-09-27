package org.akkatrading.backtest.model

sealed trait Direction

case object Long extends Direction

case object Short extends Direction

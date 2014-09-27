package org.akkatrading.backtest.model

import java.time.LocalDateTime

case class LogMessage(timestamp: LocalDateTime, intervalPnl: Double, tradePnl: Double, logMsg: String)

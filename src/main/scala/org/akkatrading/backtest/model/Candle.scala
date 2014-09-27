package org.akkatrading.backtest.model

import java.time.LocalDateTime

case class Candle(timestamp: LocalDateTime, open: Double, high: Double, low: Double, last: Double) {
  lazy val isBullish = last > low + (high - low) / 2
}

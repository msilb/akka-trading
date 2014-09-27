package org.akkatrading.backtest

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Actor
import org.akkatrading.backtest.PriceDataCsvReader.{PriceTicks, ReadTicksFromCsv}
import org.akkatrading.backtest.model.Candle

import scala.io.Source

object PriceDataCsvReader {

  case class ReadTicksFromCsv(fileName: String)

  case class PriceTicks(priceTicks: List[Candle])

}

class PriceDataCsvReader extends Actor {

  val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss")

  def receive = {
    case ReadTicksFromCsv(fileName: String) =>
      val ticks = Source.fromFile(fileName).getLines().map {
        line =>
          val tokens = line.split(";")
          if (tokens.length == 6)
            Candle(LocalDateTime.parse(tokens(0) + " " + tokens(1), dateTimeFormatter), tokens(2).toDouble, tokens(3).toDouble, tokens(4).toDouble, tokens(5).toDouble)
          else
            Candle(LocalDateTime.parse(tokens(0) + " 00:00:00", dateTimeFormatter), tokens(1).toDouble, tokens(2).toDouble, tokens(3).toDouble, tokens(4).toDouble)
      }.toList
      sender() ! PriceTicks(ticks)
  }
}

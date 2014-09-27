package org.akkatrading.live

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.akkatrading.live.EventListener.SubscribeToEvents
import spray.can.Http
import spray.can.Http.HostConnectorInfo

import scala.concurrent.duration._

object Main extends App {

  implicit val timeout = Timeout(5 seconds)
  implicit val system = ActorSystem("oanda-client")

  import org.akkatrading.live.Main.system.dispatcher

  val streamingConnectorFuture = for {
    HostConnectorInfo(hostConnector, _) <- IO(Http) ? Http.HostConnectorSetup("stream-fxpractice.oanda.com", port = 443, sslEncryption = true)
  } yield hostConnector

  val restConnectorFuture = for {
    HostConnectorInfo(hostConnector, _) <- IO(Http) ? Http.HostConnectorSetup("api-fxpractice.oanda.com", port = 443, sslEncryption = true)
  } yield hostConnector

  restConnectorFuture onSuccess {
    case restConnector =>
      val orderManager = system.actorOf(StrategyFSM.props(restConnector), "orderManager")
      val candleFetcher = system.actorOf(CandleFetcher.props(restConnector, orderManager), "candleFetcher")
      streamingConnectorFuture onSuccess {
        case streamingConnector =>
          val eventListener = system.actorOf(EventListener.props(streamingConnector, orderManager), "eventListener")
          eventListener ! SubscribeToEvents
      }
  }
}

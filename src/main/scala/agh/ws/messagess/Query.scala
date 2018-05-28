package agh.ws.messagess

import agh.ws.util.QueryCounter
import akka.actor.ActorRef

trait Query extends Request{
  val queryId: Long = QueryCounter.inc
}


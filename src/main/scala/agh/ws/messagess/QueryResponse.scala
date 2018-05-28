package agh.ws.messagess

import akka.actor.ActorRef


case class QueryResponse(requestId:Long, responses: Map[ActorRef, Response]) extends QueryResponseTrait

trait QueryResponseTrait extends Response {
  val requestId:Long
  val responses: Map[ActorRef, Response]
}


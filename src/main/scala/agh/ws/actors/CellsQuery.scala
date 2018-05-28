package agh.ws.actors

import agh.ws.messagess._
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration

object CellsQuery {
  case class CollectionTimeout(requestId: Long) extends Response
  case class CellTimedOut(requestId: Long) extends Response

  def props(cellsRef: Set[ActorRef], requestId: Long, requester: ActorRef, query: Request, timeout: FiniteDuration): Props =
    Props(new CellsQuery(cellsRef, requestId, requester, query, timeout, 1L))

}

class CellsQuery(
                         cellsRef: Set[ActorRef],
                         requestId: Long,
                         requester: ActorRef,
                         query: Request,
                         timeout:   FiniteDuration,
                         val queryId: Long
                      ) extends Actor with ActorLogging {
  import CellsQuery._
  import context.dispatcher
  val queryTimeoutTimer: Cancellable = context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    cellsRef.foreach {
      ref =>
        context.watch(ref)
        ref ! query
    }
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }


  override def receive: Receive =
    waitingForReplies(
      Map.empty,
      cellsRef
    )

  private def waitingForReplies(
                         ready: Map[ActorRef, Response],
                         pending: Set[ActorRef]
                       ): Receive = {
    case x: Response =>
      val actorRef = sender()
      receivedResponse(actorRef, x, pending, ready)

    //    case Terminated(actorRef) =>
    //      receivedResponse(actorRef, NotAvailable, pending, ready)

    case CollectionTimeout =>
      val timedOutReplies: Map[ActorRef, Response] = pending.map { ref =>
        ref -> CellsQuery.CellTimedOut(-1L)
      }.toMap
      requester ! QueryResponse(requestId, ready ++ timedOutReplies)
      context stop self

    case msg @ _ => log.warning(s"QUERY -Unknown message $msg from ${sender()}")

  }

  private def receivedResponse(
                        responseActor: ActorRef,
                        response: Response,
                        pending: Set[ActorRef],
                        ready: Map[ActorRef, Response]
                      ): Unit = {
    context.unwatch(responseActor)
    val newPending = pending - responseActor
    val newReady = ready + (responseActor -> response)
    if (newPending.isEmpty) {
      requester ! QueryResponse(requestId, newReady)
      context stop self
    } else {
      context become waitingForReplies(newReady, newPending)
    }
  }
}

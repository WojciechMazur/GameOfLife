package agh.ws.actors

import agh.ws.actors.Cell.Position
import agh.ws.messagess._
import agh.ws.models.CellRectangle
import agh.ws.util.RequestsCounter
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.ActorMaterializer

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._
import scala.concurrent.duration._

object Cell{
  val isEmpty = true
  implicit val timeout: akka.util.Timeout = akka.util.Timeout(30.seconds)

  case class Position(x: Float, y: Float)

  final case class ChangeStatus(groupId:Long,
                                override val shouldReply: Boolean=true) extends Request
  final case class GetStatus() extends Request
  final case class Status(status:Boolean, seedGroupId:Long, requestId:Long)         extends Response
  final case class StatusChanged(status:Boolean, seedGroupId:Long, requestId:Long)  extends Response
  final case class CellFullyIterated() extends Request
  final case class RegisterNeighbour(targetRef: ActorRef,
                                     recursive: Boolean = true)   extends Request
  final case class RemoveNeighbours(neighbours: Set[ActorRef],
                                    override val shouldReply: Boolean=true,
                                    recursive: Boolean=true)      extends Request
  final case class GetNeighbours()                                extends Request
  final case class NeighbourRegistered(requestId:Long)                    extends Response
  final case class NeighboursRemoved(requestId:Long, position: Position)  extends Response
  final case class Neighbours(neighbours: Set[ActorRef], requestId: Long) extends Response

  final case class Iterate() extends Request
  final case class IterationCompleted(newGrains: Map[ActorRef, Long], requestId:Long) extends Response

  def props(position: Position, id: Long): Props = Props(new Cell(position, id))
}

class Cell(position: Position, id: Long) extends Actor with ActorLogging {

  import Cell._

  implicit val system: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def receive: Receive = waitingForMessagess(
    Set.empty,
    isEmpty,
    0L,
    Map.empty
  )


  def waitingForMessagess(
                           neighbours: Set[ActorRef],
                           status: Boolean,
                           seedGroupId: Long,
                           queries: Map[Long, ActorRef])
  : Receive = {
    // Neighbouhood
    case req@RegisterNeighbour(neighbour, recursive) =>
      if (!(neighbours contains neighbour)) {
        log.debug(s"New neighbour $neighbour <--> $self")
        if (recursive)
          neighbour ! RegisterNeighbour(self)
        context watch req.targetRef
        context become waitingForMessagess(neighbours + neighbour, status, seedGroupId, queries)
      }
      sender() ! NeighbourRegistered(req.requestId)

    case Terminated(ref) =>
      val neighbour = sender()
      log.debug(s"Neighbour terminated $neighbour --> $ref")
      context unwatch neighbour

    case req@GetNeighbours() =>
      sender() ! Neighbours(neighbours, req.requestId)

    case req@RemoveNeighbours(neighboursToRemove: Set[ActorRef], shouldReply: Boolean, recursive: Boolean) =>
      val ns = if (neighboursToRemove.isEmpty) neighbours else neighboursToRemove
      ns foreach (n => {
        context unwatch n
        if (recursive)
          n ! RemoveNeighbours(Set(self), shouldReply = false)
        log.debug(s"Removing neighbour $n from $self")
      })
      if (shouldReply)
        sender() ! NeighboursRemoved(req.requestId, position)
      context become waitingForMessagess(neighbours diff ns, status, seedGroupId, queries)

    case NeighbourRegistered(_) => log.debug(s"Recursively registered neighbour $self to ${sender()}")

    // Status
    case req@ChangeStatus(seedGroup, shouldReply) =>
      if (isEmpty) {
        val newStatus = seedGroup match {
          case 0L => isEmpty
          case _ => !isEmpty
        }
        if (shouldReply)
          sender() ! StatusChanged(newStatus, seedGroup, req.requestId)
        context become waitingForMessagess(neighbours, newStatus, seedGroup, queries)
        context.parent ! StatusChanged(newStatus, seedGroup, req.requestId)
      } else
        sender() ! StatusChanged(status, seedGroupId, req.requestId)

    case req@GetStatus() =>
      sender() ! Status(status, seedGroupId, req.requestId)

    // Behavior
    case req@Iterate() =>
      if (neighbours.nonEmpty) {
        context.actorOf(CellsQuery.props(neighbours, req.requestId, self, GetStatus(), 3.second), s"query-getStatus-${req.requestId}")
        context become waitingForMessagess(neighbours, status, seedGroupId, queries + (req.requestId -> sender()))
      } else {
        log.debug("Empty neighbours list, skipping query")
        sender() ! IterationCompleted(Map.empty, req.requestId)
      }

    case query @ QueryResponse(requestId, responses: Map[ActorRef, Response]) =>
      val sender = queries(requestId)
      if(responses.isEmpty)
        context become  waitingForMessagess(neighbours, status, seedGroupId, queries - requestId)
      else
      responses.head._2 match {
        case s: Status =>
          val emptyGrains = responses.asInstanceOf[Map[ActorRef, Status]]
            .filter { case (_, stat) => stat.status == isEmpty }
          if(emptyGrains.isEmpty){
            sender ! IterationCompleted(Map.empty, requestId)
            context.parent ! CellFullyIterated()
            context become waitingForMessagess(neighbours, status, seedGroupId, queries - requestId)
          }
          else {
            val newRequestId = RequestsCounter.inc
            context.actorOf(
              CellsQuery.props(
                emptyGrains.keySet,
                newRequestId,
                self,
                ChangeStatus(seedGroupId),
                1.second
              ))
            context become waitingForMessagess(neighbours, status, seedGroupId, queries - requestId + (newRequestId -> sender))
          }
        case s : StatusChanged =>
          val newGrains = responses.asInstanceOf[Map[ActorRef, StatusChanged]]
            .filter{
              case (ref, statusChanged) => statusChanged.seedGroupId!=CellRectangle.noGrainGroup
            }.map{
            case (ref, statusChanged) => ref -> statusChanged.seedGroupId
          }

          sender ! IterationCompleted(newGrains, requestId)
          context become waitingForMessagess(neighbours, status, seedGroupId, queries - requestId)

        case other => log.warning(s"Unknown query response: $other")
      }

    case QueryResponse(_, unknownResponses) =>
      log.warning(s"Unknown response $unknownResponses")

    case msg@_ => log.warning(s"Unknown message $msg from ${sender()}")
  }


}

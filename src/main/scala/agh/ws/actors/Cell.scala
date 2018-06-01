package agh.ws.actors

import agh.ws.actors.Cell.Position
import agh.ws.messagess._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.reflect.runtime.universe.{typeTag, TypeTag}

object Cell{
  val alive = true
  val dead = false
  implicit val timeout: akka.util.Timeout = akka.util.Timeout(30.seconds)

  case class Position(x: Float, y: Float)

  final case class ChangeStatus(status: Boolean,
                                override val shouldReply: Boolean=true) extends Request
  final case class GetStatus() extends Request
  final case class Status(status:Boolean, requestId:Long)         extends Response
  final case class StatusChanged(status:Boolean, requestId:Long)  extends Response

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
  final case class IterationCompleted(status:Boolean, requestId:Long) extends Response

  def props(position: Position, id: Long): Props = Props(new Cell(position, id))
}

class Cell(position: Position, id: Long) extends Actor with ActorLogging {

  import Cell._

  implicit val system: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def receive: Receive = waitingForMessagess(
    Set.empty,
    dead,
    Map.empty
  )


  def waitingForMessagess(
                           neighbours: Set[ActorRef],
                           status: Boolean,
                           queries: Map[Long, ActorRef])
  : Receive = {
    // Neighbouhood
    case req@RegisterNeighbour(neighbour, recursive) =>
      if (!(neighbours contains neighbour)) {
        log.debug(s"New neighbour $neighbour <--> $self")
        if (recursive)
          neighbour ! RegisterNeighbour(self)
        context watch req.targetRef
        context become waitingForMessagess(neighbours + neighbour, status, queries)
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
      context become waitingForMessagess(neighbours diff ns, status, queries)

    case NeighbourRegistered(_) => log.debug(s"Recursively registered neighbour $self to ${sender()}")

    // Status
    case req@ChangeStatus(newStatus, shouldReply) =>
      log.debug(s"Changing status of $self to $newStatus")
      if (shouldReply)
        sender() ! StatusChanged(newStatus, req.requestId)
      context become waitingForMessagess(neighbours, newStatus, queries)

    case req@GetStatus() =>
      sender() ! Status(status, req.requestId)

    // Behavior
    case req@Iterate() =>
      if (neighbours.nonEmpty) {
        context.actorOf(CellsQuery.props(neighbours, req.requestId, self, GetStatus(), 3.second), s"query-getStatus-${req.requestId}")
        context become waitingForMessagess(neighbours, status, queries + (req.requestId -> sender()))
      } else {
        log.debug("Empty neighbours list, skipping query")
        sender() ! IterationCompleted(iterate(Map.empty, status), req.requestId)
      }

    case QueryResponse(requestId, responses: Map[ActorRef, Status]) =>
      //      log.debug(s"Received query response with id $requestId")
      val newStatus = iterate(responses, status)
      val sender = queries(requestId)
      context become waitingForMessagess(neighbours, status, queries - requestId)

      sender ! IterationCompleted(newStatus, requestId)

    case QueryResponse(_, unknownResponses) =>
      log.warning(s"Unknown response $unknownResponses")

    case msg@_ => log.warning(s"Unknown message $msg from ${sender()}")
  }

  def iterate(responses: Map[ActorRef, Status], lastStatus: Boolean): Boolean = {
    val aliveNeighbours = responses.values.count(_.status == alive)

    aliveNeighbours match {
      case 3 => alive
      case n if n > 3 || n < 2 => dead
      case _ => lastStatus
    }
  }
}

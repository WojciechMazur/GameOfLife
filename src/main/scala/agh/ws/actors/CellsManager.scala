package agh.ws.actors

import agh.ws._
import agh.ws.actors.Cell.{CellFullyIterated, ChangeStatus, Iterate, IterationCompleted, NeighbourRegistered, NeighboursRemoved, Position, RegisterNeighbour, RemoveNeighbours, StatusChanged}
import agh.ws.messagess._
import agh.ws.util.BoundiresBehavior.Repetetive
import agh.ws.util._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.reflect.runtime.universe._

object CellsManager{
  final case class RegisterCell(position: Position) extends Request
  final case class CellRegistered(requestId: Long, actorRef: ActorRef, position: Position, cellId: Long) extends Response

  final case class ChangeNeighbourhoodModel(neighbourhoodModel: NeighbourhoodModel, boundiresBehavior: BoundiresBehavior) extends Request
  final case class NeighbourhoodModelChanged(requestId:Long) extends Response
  final object     EmptyIteration
  def props(boundries: Boundries, boundiresBehavior: BoundiresBehavior, cellsOffset:Float, neighbourhoodModel: NeighbourhoodModel): Props = Props(new CellsManager(cellsOffset, neighbourhoodModel, boundiresBehavior)(boundries))

  val matchPrecision = GameOfLifeApp.size/4
}

class CellsManager(
        cellsOffset:Float,
        initialModel: NeighbourhoodModel,
        initialBehavior: BoundiresBehavior
    )( implicit boundires: Boundries)
  extends Actor with ActorLogging {

  import CellsManager._

  private val positionToRef: mutable.Map[Position, ActorRef] = scala.collection.mutable.HashMap[Position, ActorRef]()
  private val grains: mutable.Set[ActorRef] = mutable.HashSet[ActorRef]()
  private val refToCellId: mutable.Map[ActorRef, Long] = mutable.HashMap[ActorRef, Long]()
  implicit private val cellIdToRef: mutable.Map[Long, ActorRef] = scala.collection.mutable.HashMap[Long, ActorRef]()
  implicit val system: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()


  override def receive: Receive = waitingForMessagess(
    Map.empty,
    initialModel,
    Repetetive
  )

  def waitingForMessagess(
           implicit pending: Map[Long, ActorRef],
           neighbourhoodModel: NeighbourhoodModel,
           boundiresBehavior: BoundiresBehavior
     ): Receive = {
    case req@RegisterCell(position) =>
      log.debug(s"Cell reqister request #${req.requestId} - $position")
      val cellId = CellsCounter.inc
      val newCell = context.actorOf(Cell.props(position, cellId), s"cell-${position.x}-${position.y}-$cellId")
      context.watch(newCell)
      cellIdToRef += (cellId -> newCell)
      refToCellId += (newCell -> cellId)
      sender() ! CellRegistered(req.requestId, newCell, position, cellId)
      registerInNeighbourhood(position, newCell, neighbourhoodModel, boundiresBehavior)

    case response@NeighbourRegistered(_) =>
      log.debug(s"Registered cell neighbour of ${sender()}")
      receivedResponse(pending, response, neighbourhoodModel, boundiresBehavior)

    case req@Iterate() =>
      if(grains.isEmpty)
        sender() ! EmptyIteration
      else {
        GameOfLifeApp.iterationCounter.inc
        val requester = sender()
        val query = context.actorOf(CellsQuery.props(
          grains.toSet,
          req.requestId,
          requester,
          Iterate(),
          5.seconds
        ), s"query-iterate-${req.requestId}")
        pendingRequest(pending, requester, req, neighbourhoodModel, boundiresBehavior)
      }

    case CellFullyIterated() =>
      grains -= sender()

    case req @ ChangeNeighbourhoodModel(model, behavior) =>
      log.info("Changing neighbourhood")
      context.actorOf(CellsQuery.props(
          cellIdToRef.values.toSet,
          req.requestId,
          self,
          RemoveNeighbours(Set.empty, recursive = false),
          5.seconds
        ))

      context become waitingForMessagess(
        pending + (req.requestId -> sender()),
        model,
        behavior
      )

    case StatusChanged(status, seedGroupId,_) =>
      val ref = sender()
      status match {
        case Cell.isEmpty => grains -= ref
          log.debug(s"Cell $ref was unbinded from grain group")
        case isGrain => grains += ref
          log.debug(s"Cell $ref was binded to grain group $seedGroupId")
      }

    case res @ QueryResponse(_, responses) if responses.values.forall(_.isInstanceOf[NeighboursRemoved]) =>
      responses.foreach{
        case (ref: ActorRef, response: NeighboursRemoved) =>
          log.debug(s"Setting new neighbourhood of $ref")
          registerInNeighbourhood(response.position, ref, neighbourhoodModel, boundiresBehavior)
        case unknown => log.warning(s"Unknown response $unknown")
      }
      pending(res.requestId) ! NeighbourhoodModelChanged(res.requestId)
      context become waitingForMessagess(pending - res.requestId, neighbourhoodModel, boundiresBehavior)

    case msg@_ => log.warning(s"MANAGER - Unknown message $msg from ${sender()}")


  }


  def pendingRequest(pending: Map[Long, ActorRef], senderRef: ActorRef, request: Request, nModel: NeighbourhoodModel, behavior: BoundiresBehavior): Unit = {
    context become waitingForMessagess(pending + (request.requestId -> senderRef), nModel, behavior)
  }

  def pendingRequest(pending: Map[Long, ActorRef], senderRef: ActorRef, requests: Iterable[Request], nModel: NeighbourhoodModel, behavior: BoundiresBehavior): Unit = {
    val newRequests = requests
      .map(req => req.requestId -> senderRef)
      .toMap
    context become waitingForMessagess(pending ++ newRequests, nModel, behavior)
  }

  def receivedResponse(pending: Map[Long, ActorRef], response: Response, nModel:NeighbourhoodModel, behavior: BoundiresBehavior): Unit = {
    val ref = pending.getOrElse(response.requestId, self)
    if (ref != self)
      ref ! response
    context become waitingForMessagess(pending - response.requestId, nModel, behavior)
  }

  private def registerInNeighbourhood(
             position: Position,
             newCell: ActorRef,
             nModel: NeighbourhoodModel,
             behavior: BoundiresBehavior)
             (implicit pendingRequests: Map[Long, ActorRef])
    : Unit = {
    val neighbours = nModel.findCellNeighbours(position, cellsOffset, behavior)
    val isRecursive = nModel match {
      case HexagonalRandom | PentagonalRandom => false
      case _ => true
    }

    val requests = neighbours
      .map(ref => {
        val request = RegisterNeighbour(newCell, isRecursive)
        ref ! request
        request
      })
    pendingRequest(pendingRequests, self, requests, nModel, behavior)
  }


}
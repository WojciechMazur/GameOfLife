package agh.ws.actors

import agh.ws._
import agh.ws.actors.Cell.{CellFullyIterated, ChangeStatus, Iterate, IterationCompleted, NeighbourRegistered, NeighboursRemoved, Position, RegisterNeighbour, RemoveNeighbours, StatusChanged}
import agh.ws.messagess._
import agh.ws.util._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.collection.mutable
import scala.concurrent.duration._
import scala.reflect.runtime.universe._

object CellsManager{
  final case class RegisterCell(position: Position) extends Request
  final case class CellRegistered(requestId: Long, actorRef: ActorRef, position: Position, cellId: Long) extends Response

  final case class ChangeNeighbourhoodModel(neighbourhoodModel: NeighbourhoodModel) extends Request
  final case class NeighbourhoodModelChanged(requestId:Long) extends Response
  def props(boundries: Boundries, boundiresBehavior: BoundiresBehavior, cellsOffset:Float, neighbourhoodModel: NeighbourhoodModel): Props = Props(new CellsManager(cellsOffset, neighbourhoodModel)(boundries, boundiresBehavior))

  val matchPrecision = GameOfLifeApp.size/4
}

class CellsManager(
                    cellsOffset:Float,
                    initialModel: NeighbourhoodModel
                  )(
  implicit boundires: Boundries,
  boundiresBehavior: BoundiresBehavior)
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
    initialModel
  )

  def waitingForMessagess(implicit pending: Map[Long, ActorRef], neighbourhoodModel: NeighbourhoodModel): Receive = {
    case req@RegisterCell(position) =>
      log.debug(s"Cell reqister request #${req.requestId} - $position")
      val cellId = CellsCounter.inc
      val newCell = context.actorOf(Cell.props(position, cellId), s"cell-${position.x}-${position.y}-$cellId")
      context.watch(newCell)
      cellIdToRef += (cellId -> newCell)
      refToCellId += (newCell -> cellId)
      sender() ! CellRegistered(req.requestId, newCell, position, cellId)
      registerInNeighbourhood(position, newCell, neighbourhoodModel)

    case response@NeighbourRegistered(_) =>
      log.debug(s"Registered cell neighbour of ${sender()}")
      receivedResponse(pending, response, neighbourhoodModel)

    case req@Iterate() =>
      GameOfLifeApp.iterationCounter.inc
      val requester = sender()
      val query = context.actorOf(CellsQuery.props(
        grains.toSet,
        req.requestId,
        requester,
        Iterate(),
        5.seconds
      ), s"query-iterate-${req.requestId}")
      pendingRequest(pending, requester, req, neighbourhoodModel)

    case CellFullyIterated() =>
      grains -= sender()

    case req @ ChangeNeighbourhoodModel(model) =>
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
        model
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
      log.info(s"Got query response $res")
      responses.foreach{
        case (ref: ActorRef, response: NeighboursRemoved) =>
          log.debug(s"Setting new neighbourhood of $ref")
          registerInNeighbourhood(response.position, ref, neighbourhoodModel)
        case unknown => log.warning(s"Unknown response $unknown")
      }
      pending(res.requestId) ! NeighbourhoodModelChanged(res.requestId)
      context become waitingForMessagess(pending - res.requestId, neighbourhoodModel)

    case msg@_ => log.warning(s"MANAGER - Unknown message $msg from ${sender()}")


  }


  def pendingRequest(pending: Map[Long, ActorRef], senderRef: ActorRef, request: Request, nModel: NeighbourhoodModel): Unit = {
    context become waitingForMessagess(pending + (request.requestId -> senderRef), nModel)
  }

  def pendingRequest(pending: Map[Long, ActorRef], senderRef: ActorRef, requests: Iterable[Request], nModel: NeighbourhoodModel): Unit = {
    val newRequests = requests
      .map(req => req.requestId -> senderRef)
      .toMap
    context become waitingForMessagess(pending ++ newRequests, nModel)
  }

  def receivedResponse(pending: Map[Long, ActorRef], response: Response, nModel:NeighbourhoodModel): Unit = {
    val ref = pending.getOrElse(response.requestId, self)
    if (ref != self)
      ref ! response
    context become waitingForMessagess(pending - response.requestId, nModel)
  }

  private def registerInNeighbourhood(position: Position, newCell: ActorRef, nModel: NeighbourhoodModel)(implicit pendingRequests: Map[Long, ActorRef]): Unit = {
    val neighbours = nModel.findCellNeighbours(position, cellsOffset)
    val isRecoursive = nModel match {
      case HexagonalRandom | PentagonalRandom => false
      case _ => true
    }

    val requests = neighbours
      .map(ref => {
        val request = RegisterNeighbour(newCell, isRecoursive)
        ref ! request
        request
      })
    pendingRequest(pendingRequests, self, requests, nModel)
  }


}
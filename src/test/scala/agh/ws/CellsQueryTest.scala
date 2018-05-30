package agh.ws

import agh.ws.actors.{Cell, CellsManager, CellsQuery}
import agh.ws.util.{BoundiresBehavior, Boundries}
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import org.scalatest.FlatSpec

import scala.concurrent.duration._

class CellsQueryTest extends FlatSpec{

  import agh.ws.actors.Cell._
  implicit val system: ActorSystem = ActorSystem("game-of-life")
  val manager: ActorRef = system.actorOf(CellsManager.props(
    Boundries(100, 100),
    BoundiresBehavior.Repetetive,
    1.0f))


  "Manager Querry" should "return valid response" in {
    val position = Position(0.5f,0.25f)
    val position2 = Position(0.1f,0.2f)
    val position3 = Position(11.0f,5.0f)
    val cell: ActorRef = system.actorOf(Cell.props(position,11))
    val cell2: ActorRef = system.actorOf(Cell.props(position2,12))
    val cell3: ActorRef = system.actorOf(Cell.props(position3,13))
    val refs = Set(cell, cell2, cell3)

    val probeStatus = TestProbe()
    val responseProbe = TestProbe()

    val query = system.actorOf(CellsQuery.props(refs, 1L, responseProbe.ref, GetStatus(), 10 seconds))
    val res = responseProbe.expectMsgType[agh.ws.messagess.QueryResponse]
    assert(res.requestId==1L)
    assert(res.responses.size==3)
  }
}

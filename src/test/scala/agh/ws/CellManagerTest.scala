package agh.ws

import agh.ws.actors.CellsManager
import agh.ws.actors.CellsManager.{CellRegistered, RegisterCell}
import agh.ws.util.{BoundiresBehavior, Boundries}
import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestProbe}
import org.scalatest.FlatSpec

class CellManagerTest extends FlatSpec{

  import agh.ws.actors.Cell._
  implicit val system: ActorSystem = ActorSystem("game-of-life")
  val manager: TestActorRef[CellsManager] = TestActorRef(CellsManager.props(
    Boundries(100, 100),
    BoundiresBehavior.Repetetive,
    1.0f))

  "Cell manager" must "register new cells" in {

    val probe = TestProbe()
    manager.tell(RegisterCell(Position(0.0f, 0.0f)), probe.ref)
    probe.expectMsgType[CellRegistered]
  }

  it must "return valid set of neighbours" in {
    val registerProbe = TestProbe()
    manager.tell(RegisterCell(Position(0.0f, 0.0f)), registerProbe.ref)
    val cell1 = registerProbe.expectMsgType[CellRegistered]
    manager.tell(RegisterCell(Position(1.0f, 0.0f)), registerProbe.ref)
    val cell2 = registerProbe.expectMsgType[CellRegistered]
    manager.tell(RegisterCell(Position(2.0f, 0.0f)), registerProbe.ref)

    val nProbe = TestProbe()

    cell1.actorRef tell(GetNeighbours(), nProbe.ref)
    val neighbours = nProbe.expectMsgType[Neighbours]
    assert(neighbours.neighbours.size==1)

    cell2.actorRef tell(GetNeighbours(), nProbe.ref)
    assert(nProbe.expectMsgType[Neighbours].neighbours.size==2)
  }

  it must "return valid cells status" in {
    val probe = TestProbe()
    val registerProbe = TestProbe()
    val position1 = Position(0.0f, 0.0f)
    val position2 = Position(0.1f, 0.0f)
    manager.tell(RegisterCell(position1), registerProbe.ref)
    val cell1 = registerProbe.expectMsgType[CellRegistered].actorRef
    cell1 ! ChangeStatus(alive)
    cell1 tell(GetStatus(), probe.ref)
    assert(probe.expectMsgType[Status].status==alive)

    manager.tell(RegisterCell(position2), registerProbe.ref)
    val cell2 = registerProbe.expectMsgType[CellRegistered].actorRef
    cell2 tell(GetStatus(), probe.ref)
    assert(probe.expectMsgType[Status].status==dead)
  }
}

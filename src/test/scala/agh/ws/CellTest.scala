package agh.ws

import agh.ws.actors.Cell
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import org.scalatest.FlatSpec

class CellTest extends FlatSpec{
  import Cell._
  implicit val system: ActorSystem = ActorSystem("game-of-life")

  "Cell" should "register neighbours" in {
    val cell: ActorRef = system.actorOf(Cell.props(Position(0.5f,0.25f), 1))
    val cell2: ActorRef = system.actorOf(Cell.props(Position(0.1f, 0.2f), 2))
    val cell3: ActorRef = system.actorOf(Cell.props(Position(11.0f, 5.0f), 3))

    val probeRegister1 = TestProbe()(system)
    val probeRegister2 = TestProbe()(system)
    val probeNeighbours = TestProbe()(system)
    val probeNeighbours2 = TestProbe()
    val probeInvalidRegister = TestProbe()(system)


    cell.tell(RegisterNeighbour(cell2), probeRegister1.ref)
    cell.tell(RegisterNeighbour(cell3), probeRegister2.ref)
    cell.tell(RegisterNeighbour(cell3), probeInvalidRegister.ref)

    probeRegister1.expectMsgType[NeighbourRegistered]
    probeRegister2.expectMsgType[NeighbourRegistered]
    probeInvalidRegister.expectMsgType[NeighbourRegistered]

    cell.tell(GetNeighbours(), probeNeighbours.ref)
    val response = probeNeighbours.expectMsgType[Neighbours]

    cell2.tell(GetNeighbours(), probeNeighbours2.ref)
    val response2 = probeNeighbours2.expectMsgType[Neighbours]

    assert(response.neighbours.size == 2)
    assert(response2.neighbours.size == 1)
  }

  it must "overpopulated death" in {
    val cell: ActorRef = system.actorOf(Cell.props(null,1))
    val cell1: ActorRef = system.actorOf(Cell.props(null,2))
    val cell2: ActorRef = system.actorOf(Cell.props(null,3))
    val cell3: ActorRef = system.actorOf(Cell.props(null,4))
    val cell4: ActorRef = system.actorOf(Cell.props(null,5))


    val registerProbe = TestProbe()
    cell.tell(RegisterNeighbour(cell1),registerProbe.ref )
    cell.tell(RegisterNeighbour(cell2),registerProbe.ref )
    cell.tell(RegisterNeighbour(cell3),registerProbe.ref )
    cell.tell(RegisterNeighbour(cell4),registerProbe.ref )
    registerProbe.expectMsgType[NeighbourRegistered]
    val probe = TestProbe()
    cell.tell(Iterate(), probe.ref)
    val response = probe.expectMsgType[IterationCompleted]
    assert(response.status == dead)
  }

  it must "loneliness death" in {
    val cell: ActorRef = system.actorOf(Cell.props(null,6))

    val probe = TestProbe()
    cell.tell(Iterate(), probe.ref)
    val response = probe.expectMsgType[IterationCompleted]
    assert(response.status == dead)
  }

  it must "get alive" in {
    val cell: ActorRef = system.actorOf(Cell.props(null, 7))
    val cell1: ActorRef = system.actorOf(Cell.props(null,8))
    val cell2: ActorRef = system.actorOf(Cell.props(null,9))
    val cell3: ActorRef = system.actorOf(Cell.props(null,10))
    cell1 ! ChangeStatus(alive)
    cell2 ! ChangeStatus(alive)

    val registerProbe = TestProbe()
    cell.tell(RegisterNeighbour(cell1), registerProbe.ref)
    cell.tell(RegisterNeighbour(cell2), registerProbe.ref)

    val probe = TestProbe()
    cell.tell(Iterate(), probe.ref)
    val response = probe.expectMsgType[IterationCompleted]
    assert(response.status == alive)

    cell.tell(RegisterNeighbour(cell3), registerProbe.ref)
    val probe2 = TestProbe()
    cell.tell(Iterate(), probe2.ref)
    val response2 = probe2.expectMsgType[IterationCompleted]
    assert(response2.status == alive)
  }
}

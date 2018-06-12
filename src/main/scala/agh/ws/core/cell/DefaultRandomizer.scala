package agh.ws.core.cell

import agh.ws.actors.Cell.{ChangeStatus, StatusChanged}
import agh.ws.models.CellRectangle
import agh.ws.util.GrainsCounter
import akka.actor.ActorRef
import akka.pattern.ask

import scala.util.{Failure, Random, Success}

object DefaultRandomizer extends CellRandomizer {
  override val name: String = "Default"

  override def randomize(maxCells: Long,
                         cellsRectangles: Map[Long, CellRectangle],
                         refsOfCells: Map[ActorRef, Long]
                        ): Unit = {
    this.purge(refsOfCells, cellsRectangles)
    randomizeCells(Set.empty, refsOfCells.keySet, maxCells)(cellsRectangles, refsOfCells)
  }

  @scala.annotation.tailrec
  def randomizeCells(ready: Set[ActorRef],
                     rest: Set[ActorRef],
                     maxCells: Long)
                    (implicit cellsRectangles: Map[Long, CellRectangle],
                     refsOfCells: Map[ActorRef, Long]): Unit = {
    ready.size match {
      case `maxCells` => ()
      case _ =>
        val groupId = GrainsCounter.inc
        val next = Random.nextInt(rest.size)
        val ref = rest.toVector(next)
        ref ? ChangeStatus(groupId) onComplete {
          case Success(StatusChanged(status, seedGroupId, _)) =>
            cellsRectangles(refsOfCells(ref)).grainGroupId.value = seedGroupId
          case Failure(t) => log.warn(t.toString)
          case Success(s) => log.warn(s"Unknown response $s")
        }
        randomizeCells(ready + ref, rest - ref, maxCells)
    }
  }
}

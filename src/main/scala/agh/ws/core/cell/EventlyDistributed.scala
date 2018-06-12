package agh.ws.core.cell

import agh.ws.GameOfLifeApp
import agh.ws.actors.Cell.{ChangeStatus, StatusChanged}
import agh.ws.core.cell.DefaultRandomizer.log
import agh.ws.models.CellRectangle
import agh.ws.util.GrainsCounter
import akka.actor.ActorRef
import akka.pattern.ask

import scala.util.{Failure, Random, Success}

object EventlyDistributed extends CellRandomizer {
  override val name: String = "Evenly distributed"

  override def randomize(maxCells: Long,
                         cellsRectangles: Map[Long, CellRectangle],
                         refsOfCells: Map[ActorRef, Long]
                        )
  : Unit = {
    this.purge(refsOfCells, cellsRectangles)

    val cellsRefs: Map[Long, ActorRef] = refsOfCells.map(_.swap)
    val rows = GameOfLifeApp.cellsY
    val cols = GameOfLifeApp.cellsX

    val distance = Math.ceil(Math.sqrt( (rows * cols) / maxCells.toDouble)).toInt

    for {
      iY <- 0 until(cols, distance)
      iX <- 0 until(rows, distance)
    } {
      val id = iY.toLong * cols + iX + 1
      val groupId = GrainsCounter.inc
      val ref = cellsRefs(id)

      ref ? ChangeStatus(groupId) onComplete {
        case Success(StatusChanged(_, seedGroupId, _)) =>
          cellsRectangles(id).grainGroupId.value = seedGroupId
        case Failure(t) => log.warn(t.toString)
        case Success(s) => log.warn(s"Unknown response $s")
      }
    }
  }
}

package agh.ws.core.cell

import agh.ws.actors.Cell.ChangeStatus
import agh.ws.models.CellRectangle
import akka.actor.ActorRef
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.collection.immutable.HashMap
import scala.concurrent.ExecutionContextExecutor

trait CellRandomizer {
  protected val log: Logger = LoggerFactory.getLogger(getClass.getName)
  val name: String

  implicit protected val timeout: akka.util.Timeout = 5.seconds
  implicit protected val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def randomize(maxCells: Long,
                cellsRectangles: Map[Long, CellRectangle],
                refsOfCells: Map[ActorRef, Long]
               ): Unit

  protected def purge(refsOfCells: Map[ActorRef, Long],
                    cellsRectangles: Map[Long, CellRectangle]): Unit = {
    refsOfCells.keys.foreach( ref => {
      cellsRectangles(refsOfCells(ref)).grainGroupId.value=CellRectangle.noGrainGroup
      ref ! ChangeStatus(0L, shouldReply = false)
    })
  }
}

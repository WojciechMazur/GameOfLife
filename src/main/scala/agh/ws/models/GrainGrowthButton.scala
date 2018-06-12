package agh.ws.models

import agh.ws.GameOfLifeApp
import agh.ws.GameOfLifeApp.{cellsX, cellsY, initCounter}
import agh.ws.actors.Cell.{ChangeStatus, Iterate, IterationCompleted, Position}
import agh.ws.actors.CellsManager.EmptyIteration
import agh.ws.actors.CellsQuery.CellTimedOut
import agh.ws.messagess.QueryResponse
import akka.actor.ActorRef
import akka.pattern.ask
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scalafx.beans.property.BooleanProperty
import scalafx.scene.control.Button
import scalafx.scene.input.MouseEvent
import scala.concurrent.duration._
import scalafx.Includes._
import scalafx.application.Platform

class GrainGrowthButton(
                     cellsManager: ActorRef,
                     refsOfCells: mutable.HashMap[ActorRef, Long],
                     cellsRectangles: mutable.HashMap[Long, CellRectangle]
                   ) extends Button {
  implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  val isStarted = BooleanProperty(false)
  disable <== when(initCounter.get === cellsX*cellsY.toLong) choose false otherwise true
  text <== when(isStarted) choose "Stop grain growth" otherwise "Start grain growth"

  onMouseClicked = {
    (_: MouseEvent) =>
      isStarted.value = !isStarted.value
      if (isStarted.value)
        iterate()
  }

  def iterate(): Unit = {
    implicit val iterateTimeout: akka.util.Timeout = 20.seconds
    (cellsManager ? Iterate()) onComplete {
      case Success(QueryResponse(_, responses)) =>
//        println(responses.size)
        responses.foreach {
          case (_, result: IterationCompleted) =>
            result.newGrains.foreach {
              case (ref, groupId) =>
                Platform.runLater {
                  cellsRectangles(refsOfCells(ref)).grainGroupId.value = groupId
                }
            }
          case (ref, timeout: CellTimedOut) => ()
        }
        if (isStarted.value)
          iterate()
      case Success(EmptyIteration) =>
        Platform.runLater {
          isStarted.value = false
        }
      case Failure(t) => logger.warn(s"Iteration failed -  $t")
    }
  }
}


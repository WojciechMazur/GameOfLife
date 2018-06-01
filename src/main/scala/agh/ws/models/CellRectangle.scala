package agh.ws.models

import agh.ws.GameOfLifeApp
import agh.ws.GameOfLifeApp.{changeNeighbourhoodModel, grainColors}
import agh.ws.actors.Cell.{ChangeStatus, GetNeighbours, Neighbours, Position, StatusChanged}
import agh.ws.util.{GrainsCounter, NeighbourhoodModel}
import akka.actor.ActorRef
import akka.event.slf4j.Logger

import scalafx.beans.property.{BooleanProperty, FloatProperty, LongProperty, ObjectProperty}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.Includes._
import akka.pattern.ask

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scalafx.beans.value.ObservableValue
import scalafx.scene.input.MouseEvent

object CellRectangle{
  lazy val colorAlive: Color = scalafx.scene.paint.Color.Green
  lazy val colorDead : Color = scalafx.scene.paint.Color.Red
  lazy val noGrainGroup = 0L
}

class CellRectangle(implicit val executionContext: ExecutionContext) extends Rectangle {
  val logger = Logger(getClass.getName)

  val cellId = LongProperty(0L)
  val isEmpty = BooleanProperty(true)
  val grainGroupId = LongProperty(0L)
  val position: ObjectProperty[Position] = ObjectProperty[Position](Position(this.x.value.toFloat, this.y.value.toFloat))
  val cellRef: ObjectProperty[ActorRef] = ObjectProperty[ActorRef](ActorRef.noSender)

  private def newColor(colors:Set[Color]):Color = {
    val color = Color.rgb(
      scala.util.Random.nextInt(255),
      scala.util.Random.nextInt(255),
      scala.util.Random.nextInt(255)
    )
    if(colors.exists(c => c.red==color.red && c.blue==color.blue && c.green==color.green))
      newColor(colors)
    else
      color
  }
    grainGroupId.onChange {
      (source, oldVal, newVal) => {
        val color: Color =  newVal.longValue() match {
          case value => grainColors.getOrElse(grainGroupId.value,
            {
              val color = newColor(grainColors.values.toSet)
              grainColors += (grainGroupId.value -> color)
              color
            })
        }
        fill.value = color
      }
    }

  onMouseClicked = (event: MouseEvent) => {
    import scala.concurrent.duration._
    implicit val timeout: akka.util.Timeout = 1.second
    event.clickCount match {
      case 1 => {
        cellRef.value ? ChangeStatus(
          if(isEmpty.value) GrainsCounter.inc else 0L
        ) onComplete {
          case Success(StatusChanged(newStatus,groupId,  _)) =>
            grainGroupId.value = groupId
            isEmpty.value = newStatus
          case Failure(t) => logger.warn(s"Failed to changes status of cell rectangle at position ${x.value}:${y.value}")
        }
      }
      case v => {
        cellRef.value ? GetNeighbours() onComplete {
          case Success(Neighbours(neighbours, _)) => {
            val rects = neighbours
              .flatMap(GameOfLifeApp.refsOfCells.get)
              .flatMap(GameOfLifeApp.cellsRectangles.get)
            rects.foreach(rec =>
              rec.stroke.value = Color.CornflowerBlue)
            Thread.sleep(1000)
            rects.foreach(rect => rect.stroke.value = Color.Transparent)
          }
        }
      }
    }
  }
}

package agh.ws.models

import agh.ws.actors.Cell.{ChangeStatus, Position, StatusChanged}
import akka.actor.ActorRef
import akka.event.slf4j.Logger

import scalafx.beans.property.{BooleanProperty, FloatProperty, ObjectProperty}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.Includes._
import akka.pattern.ask

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scalafx.scene.input.MouseEvent

object CellRectangle{
  lazy val colorAlive: Color = scalafx.scene.paint.Color.Green
  lazy val colorDead : Color = scalafx.scene.paint.Color.Red
}

class CellRectangle(implicit val executionContext: ExecutionContext) extends Rectangle{
  val logger = Logger(getClass.getName)

  val isAlive = BooleanProperty(false)
  val position: ObjectProperty[Position] = ObjectProperty[Position](Position(this.x.value.toFloat, this.y.value.toFloat))
  val cellRef:  ObjectProperty[ActorRef] = ObjectProperty[ActorRef](ActorRef.noSender)

  fill <== when (isAlive) choose CellRectangle.colorAlive otherwise CellRectangle.colorDead

  onMouseClicked = (event: MouseEvent) => {
    import scala.concurrent.duration._
    implicit val timeout: akka.util.Timeout = 1.second
    cellRef.value ? ChangeStatus(!isAlive.value) onComplete{
      case Success(StatusChanged(newStatus, _)) => isAlive.value = newStatus
      case Failure(t) => logger.warn(s"Failed to changes status of cell rectangle at position ${x.value}:${y.value}")
    }
  }

}

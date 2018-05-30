package agh.ws.util

import agh.ws.GameOfLifeApp
import agh.ws.GameOfLifeApp.{cellsX, cellsY, size, spacing}
import agh.ws.actors.Cell.Position


sealed trait BoundiresBehavior {
  def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position]
}
object BoundiresBehavior {

  object Repetetive extends BoundiresBehavior {
    override def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position] = {
      val iX = ((that.x+0.1)/ offset).toInt
      val nX: Int = iX + direction.directionX
      val posX = offset * (nX match {
        case v if v < 0 => cellsX-1
        case v if v>=cellsX => v % cellsX
        case v => v
      })
//      val posX = that.x + direction.directionX * offset match {
//        case v if v < 0 => boundires.sizeX
//        case v if v > boundires.sizeX => 0
//        case v => v
//      }

      val iY = ((that.y+0.1)/offset).toInt
      val nY = iY + direction.directionY
      val posY = offset * (nY match {
        case v if v < 0 => cellsY-1
        case v if v >= cellsY => v % cellsY
        case v => v
      })
//      val posY = that.y + direction.directionY * offset match {
//        case v if v < 0 => boundires.sizeY
//        case v if v > boundires.sizeY => 0
//        case v => v
//      }
      Some(Position(posX, posY))
    }
  }

  object Strict extends BoundiresBehavior {
    override def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position] = {
      val posX = that.x + direction.directionX * offset match {
        case v if v < 0 || v > boundires.sizeX => None
        case v => Some(v)
      }
      val posY = that.y + direction.directionY * offset match {
        case v if v < 0 || v > boundires.sizeY => None
        case v => Some(v)
      }
      (posX, posY) match {
        case (Some(x), Some(y)) => Some(Position(x, y))
        case _ => None
      }
    }
  }

  object Purge extends BoundiresBehavior {
    override def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position] = None
  }

}

case class Boundries(sizeX: Float, sizeY:Float)

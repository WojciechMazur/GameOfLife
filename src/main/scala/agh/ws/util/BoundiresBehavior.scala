package agh.ws.util

import agh.ws.GameOfLifeApp
import agh.ws.GameOfLifeApp.{cellsX, cellsY, size, spacing}
import agh.ws.actors.Cell.Position


sealed trait BoundiresBehavior {
  val name: String
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

      val iY = ((that.y+0.1)/offset).toInt
      val nY = iY + direction.directionY
      val posY = offset * (nY match {
        case v if v < 0 => cellsY-1
        case v if v >= cellsY => v % cellsY
        case v => v
      })

      Some(Position(posX, posY))
    }

    override val name: String = "Periodic"
  }

  object Strict extends BoundiresBehavior {
    override def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position] = {
      val iX = ((that.x+0.1)/ offset).toInt
      val nX: Int = iX + direction.directionX

      val posX = nX match {
        case v if v < 0 || v >= cellsX=> None
        case v => Some(v)
      }

      val iY = ((that.y+0.1)/offset).toInt
      val nY = iY + direction.directionY
      val posY = nY match {
        case v if v < 0 || v >= cellsY => None
        case v => Some(v)
      }

      (posX, posY) match {
        case (Some(x), Some(y)) => Some(Position(x*offset, y*offset))
        case _ => None
      }
    }

    override val name: String = "Non-periodic"
  }

  object Purge extends BoundiresBehavior {
    override def neighbourPosition(that: Position, direction: Direction, boundires: Boundries, offset: Float): Option[Position] = None

    override val name: String = "Clean all"
  }

}

case class Boundries(sizeX: Float, sizeY:Float)

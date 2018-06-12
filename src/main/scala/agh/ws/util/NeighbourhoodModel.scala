package agh.ws.util

import agh.ws.GameOfLifeApp.{cellsX, size, spacing}
import agh.ws.actors.Cell.Position
import agh.ws.util.Direction._
import akka.actor.ActorRef

import scala.collection.mutable
import scala.util.Random

sealed trait NeighbourhoodModel {
  def directions: Seq[Direction]
  val name:String
  def findCellNeighbours(
                          position: Position,
                          offset: Float,
                          boundiresBehavior: BoundiresBehavior
                        )(
                          implicit boundires: Boundries,
                          cells: mutable.Map[Long, ActorRef]
                        ): Seq[ActorRef] = {
    directions
      .flatMap(direction => boundiresBehavior.neighbourPosition(position, direction, boundires, offset))
      .flatMap(getIdByPosition)
      .flatMap(cells.get)
  }

  private def getIdByPosition(position: Position): Option[Long] = {
    val row: Long = (position.y / (size + spacing)).toLong
    val column: Long = (position.x / (size + spacing)).toLong
    val index = row * cellsX + column + 1
    Some(index)
  }
}

case object VonNeuman extends NeighbourhoodModel {
  override val directions: Seq[Direction] = Seq(North, East, South, West)
  override val name: String = "VonNeuman"
}

case object Moore extends NeighbourhoodModel{
  override val directions: Seq[Direction] = allDirections
  override val name: String = "Moore"
}

case object HexagonalLeft extends NeighbourhoodModel {
  override val directions: Seq[Direction] = Seq(North, East, SouthEast, South, West, NorthWest)
  override val name: String = "Hexagonal Left"
}

case object HexagonalRight extends NeighbourhoodModel {
  override val directions: Seq[Direction] = Seq(North, NorthEast, East, South, SouthWest, West)
  override val name: String = "Hexagonal Right"
}

case object HexagonalRandom extends NeighbourhoodModel{
  private val directionsSet = Vector(HexagonalLeft.directions, HexagonalRight.directions)
  override def directions: Seq[Direction] = directionsSet(Random.nextInt(directionsSet.size))

  override val name: String = "Hexagonal Random"
}

case object PentagonalRandom extends NeighbourhoodModel{
  private val directionsSet = Vector(
    Seq(North, South, SouthWest, West, NorthWest),
    Seq(North, NorthEast, East, SouthEast, South),
    Seq(East, SouthEast, South, SouthWest, West),
    Seq(North, NorthEast, East, West, NorthWest)
  )

  override def directions: Seq[Direction] = directionsSet(Random.nextInt(directionsSet.size))

  override val name: String = "Pentagonal Random"
}
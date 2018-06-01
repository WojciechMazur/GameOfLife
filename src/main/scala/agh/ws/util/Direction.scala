package agh.ws.util

sealed class Direction protected(val directionX:Int, val directionY:Int)

object Direction {
  val allDirections = Seq(North, NorthEast, East, SouthEast, South, SouthWest, West, NorthWest)
  object North extends Direction(0,-1)
  object South extends Direction(0, 1)
  object West extends Direction(-1, 0)
  object East extends Direction(1, 0)
  object NorthEast extends Direction(1,-1)
  object NorthWest extends Direction(-1,-1)
  object SouthEast extends Direction(1, 1)
  object SouthWest extends Direction(-1, 1)
}



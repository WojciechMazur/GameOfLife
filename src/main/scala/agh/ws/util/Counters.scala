package agh.ws.util

class LongCounter {
  protected var counter: Long = 0
  def get: Long = synchronized(counter)
  def inc: Long = synchronized{
    counter+=1
    counter
  }
  def reset = synchronized{
    this.counter = 0
    counter
  }
}

object RequestsCounter extends LongCounter
object QueryCounter    extends LongCounter
object CellsCounter extends LongCounter
object GrainsCounter extends LongCounter
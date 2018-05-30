package agh.ws.util

import scalafx.beans.property.LongProperty

class ObersvableLongCounter {
  private val counter: LongProperty = LongProperty(0)

  def get: LongProperty = synchronized{
    counter
  }

  def inc: LongProperty = synchronized{
    import scalafx.application.Platform
    Platform.runLater(() => {
      counter.value = counter.value + 1
      if(counter.value % 1000==0)
        println(counter.value)
    })
    counter
  }

  def reset: LongProperty = synchronized{
    counter.value=0
    counter
  }

}

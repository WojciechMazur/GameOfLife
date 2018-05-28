package agh.ws.messagess

import agh.ws.util.RequestsCounter

trait Request {
  val requestId:Long = RequestsCounter.inc
  val shouldReply: Boolean = true
}



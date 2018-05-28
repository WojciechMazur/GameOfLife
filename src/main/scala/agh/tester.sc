import agh.ws.util.RequestsCounter

trait Request {
  val requestId:Long = RequestsCounter.inc
}

case class Test(name:String) extends Request

val root = Test("a")
root.requestId

val copy = root.copy(name="b")
copy.requestId



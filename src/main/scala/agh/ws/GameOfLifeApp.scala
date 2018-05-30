package agh.ws

import agh.ws.actors.Cell.{ChangeStatus, Position, StatusChanged}
import agh.ws.actors.CellsManager.{CellRegistered, RegisterCell}
import agh.ws.actors.{Cell, CellsManager}
import agh.ws.models.{CellRectangle, IterateButton}
import agh.ws.util.{BoundiresBehavior, Boundries, LongCounter, ObersvableLongCounter}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorMaterializer

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalafx.application
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.property.StringProperty
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox}
import scalafx.scene.paint.Color._
import scalafx.Includes._
import scalafx.beans.binding.Bindings

object GameOfLifeApp extends JFXApp {
  val initCounter = new ObersvableLongCounter()
  val cellsX: Int = 200
  val cellsY: Int = 100
  val size = 6
  val spacing = 1
  val width =  cellsX*(size+spacing).toFloat
  val height = cellsY*(size+spacing).toFloat

  val initiallyAlive: StringProperty = StringProperty("0")

  val boundries: Boundries = Boundries(width, height)
  private val boundiresBehavior = BoundiresBehavior.Repetetive

  val cellsRectangles: mutable.HashMap[Long, CellRectangle] = mutable.HashMap[Long, CellRectangle]()
  val cellsRefs: mutable.HashMap[Long, ActorRef] = mutable.HashMap[Long, ActorRef]()
  val refsOfCells: mutable.HashMap[ActorRef, Long] = mutable.HashMap[ActorRef, Long]()

  implicit val system: ActorSystem = ActorSystem("Game-of-life")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout = 5.seconds
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val cellsManager: ActorRef = system.actorOf(CellsManager.props(boundries, boundiresBehavior, size + spacing), "manager")

  stage = new application.JFXApp.PrimaryStage {
    title.value = "Game of Life"
    scene = new Scene(width.toDouble, height.toDouble){
      fill = Black
      val borderPane = new BorderPane()
      val initProgress = new Label()
      initProgress.text <== Bindings.createStringBinding(
        () => s"Cells initialized: ${initCounter.get.value}/${cellsX*cellsY} : ${initCounter.get.value.toDouble / (cellsX * cellsY) * 10000 / 100}%",
        initCounter.get
      )
      val initiallyAliveTextField = new TextField()
      initiallyAliveTextField.text = (cellsX*cellsY*0.01).toInt.toString
      initiallyAlive <== initiallyAliveTextField.text
      val randomInitialyAliveButton: Button = new Button("Randomize alive"){
        onMouseClicked = {
          (_:MouseEvent) => randomAliveCells
        }
      }
      val iterateButton = new IterateButton(cellsManager, refsOfCells, cellsRectangles)

      borderPane.top = new HBox(iterateButton, initiallyAliveTextField, randomInitialyAliveButton, initProgress)
      val cellsPane = new AnchorPane()
      cellsPane.children = createCells
      borderPane.center = cellsPane
      root = borderPane
    }
    onCloseRequest = {() =>
      GameOfLifeApp.system.terminate()
      Platform.exit()}
  }



  def randomAliveCells = {
    val maxCells:Int = Try(this.initiallyAlive.value.toInt).getOrElse(0)
    val refs = refsOfCells.keys.toSet
    refs.foreach( ref => {
        ref.tell(ChangeStatus(Cell.dead, shouldReply = false), ActorRef.noSender)
        cellsRectangles(refsOfCells(ref)).isAlive.value = Cell.dead
      })
    randomizeCells(Set.empty, refs)

    @scala.annotation.tailrec
    def randomizeCells(ready: Set[ActorRef], rest: Set[ActorRef]): Unit = {
      import scala.util.Random
      ready.size match {
        case `maxCells` => ()
        case _ =>
          val next = Random.nextInt(rest.size)
          val ref = rest.toVector(next)
          ref ? ChangeStatus(Cell.alive) onComplete {
            case Success(StatusChanged(newStatus, _)) =>
              cellsRectangles(refsOfCells(ref)).isAlive.value = newStatus
          }
          randomizeCells(ready + ref, rest - ref)
      }
    }
  }

  def createCells: immutable.IndexedSeq[CellRectangle] = for (
    iY <- 0 until cellsY;
    iX <- 0 until cellsX) yield {
    val rec = new CellRectangle {
        x = iX * (size + spacing)
        y = iY * (size + spacing)
        position.value = Position(x.value.toFloat, y.value.toFloat)
        width = size
        height = size
        isAlive.value = Cell.dead
      }
      initCell(rec)
      rec
  }

  private def initCell(rec: CellRectangle, retry:Int = 1):Unit = {
    cellsManager.ask(RegisterCell(rec.position.value))(30 seconds) onComplete{
      case Success(CellRegistered(_, ref,_, id)) =>
        rec.cellRef.value = ref
        rec.cellId.value = id
        synchronized{
        refsOfCells += (ref -> id)
        cellsRectangles += (id -> rec)
        }
        initCounter.inc
      case Failure(t) =>
        rec.logger.warn(s"Retrying for $retry time, because cannot register cell: $t")
        if(retry < 10)
          initCell(rec, retry +1)
        else
          rec.logger.warn(s"Cannot register cell for maximal number of retries")
    }
  }
}


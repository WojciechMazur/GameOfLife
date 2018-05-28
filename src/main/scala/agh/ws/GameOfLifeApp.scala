package agh.ws

import agh.ws.actors.Cell.{ChangeStatus, Iterate, IterationCompleted, Position, StatusChanged}
import agh.ws.actors.CellsManager.{CellRegistered, RegisterCell}
import agh.ws.actors.{Cell, CellsManager}
import agh.ws.messagess.QueryResponse
import agh.ws.models.CellRectangle
import agh.ws.util.{BoundiresBehavior, Boundries, LongCounter}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorMaterializer

import scala.collection.{immutable, mutable}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalafx.application
import scalafx.application.JFXApp
import scalafx.beans.property.{IntegerProperty, ObjectProperty, StringProperty}
import scalafx.scene.Scene
import scalafx.scene.control.{Button, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox}
import scalafx.scene.paint.Color._
import scalafx.Includes._
import scalafx.beans.binding.NumberBinding

object GameOfLifeApp extends JFXApp {
  val width = 960.0f
  val height = 960.0f
  val registeredCounter = new LongCounter();
  val size = 10
  val spacing = 2
  val marginTop = 0
  val marginLeft = 0
  val cellsX: Int = ((width - (2 * marginLeft)) / (size + spacing)).toInt
  val cellsY: Int = ((height - (2 * marginTop)) / (size + spacing)).toInt

  val initiallyAlive: StringProperty = StringProperty("0")

  val boundries: Boundries = Boundries(width, height)
  private val boundiresBehavior = BoundiresBehavior.Strict
  val cellsRectangles: mutable.HashMap[Position, CellRectangle] = mutable.HashMap[Position, CellRectangle]()
  val cellsRefs: mutable.HashMap[Position, ActorRef] = mutable.HashMap[Position, ActorRef]()
  val refsOfCells: mutable.HashMap[ActorRef, Position] = mutable.HashMap[ActorRef, Position]()

  implicit val system: ActorSystem = ActorSystem("Game-of-life")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout = 5.seconds
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  val cellsManager: ActorRef = system.actorOf(CellsManager.props(boundries, boundiresBehavior, size + spacing))

  stage = new application.JFXApp.PrimaryStage {
    title.value = "Game of Life"
    scene = new Scene(width.toDouble, height.toDouble){
      fill = Black
      val borderPane = new BorderPane()
      val initiallyAliveTextField = new TextField()
      initiallyAlive <== initiallyAliveTextField.text
      val randomInitialyAliveButton: Button = new Button("Randomize alive"){
        onMouseClicked = {
          (_:MouseEvent) => randomAliveCells
        }
      }

      borderPane.top = new HBox(iterateButton, initiallyAliveTextField, randomInitialyAliveButton)
      val cellsPane = new AnchorPane()
      cellsPane.children = createCells
      borderPane.center = cellsPane
      root = borderPane
      onKeyPressed = {(e: KeyEvent) =>
        e.code match {
          case KeyCode.I =>
           Await.ready(iterate(), 1.second)
        }
      }
    }
  }

  def iterateButton: Button = {
    val button = new Button("Iterate once")
    button.onMouseClicked = {
      (_: MouseEvent) =>  iterate()
    }
    button
  }

  def iterate() = {
    val f = cellsManager ? Iterate()
    f onComplete {
      case Success(QueryResponse(_, responses)) =>
        responses.foreach {
          case (ref, result: IterationCompleted) =>
            val pos = refsOfCells(ref)
            val rec = cellsRectangles(pos)
            rec.isAlive.value = result.status
        }
    }
    f.mapTo[QueryResponse]

  }

  def randomAliveCells = {
    println(initiallyAlive.value)
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
        x = marginLeft + iX * (size + spacing)
        y = marginTop + iY * (size + spacing)
        position.value = Position(x.value.toFloat, y.value.toFloat)
        width = size
        height = size
        isAlive.value = Cell.dead
      }
      cellsRectangles += rec.position.value -> rec
      initCell(rec)
      rec
  }

  private def initCell(rec: CellRectangle, retry:Int = 1):Unit = {
    cellsManager.ask(RegisterCell(rec.position.value))(1 minute) onComplete{
      case Success(CellRegistered(_, ref,_)) =>
        val registered = registeredCounter.inc
        if(registered % 100 == 0)
          rec.logger.info(s"Registered $registered cells already")
        rec.cellRef <== ObjectProperty[ActorRef](ref)
        refsOfCells += (ref -> rec.position.value)
      case Failure(t) =>
        rec.logger.debug(s"Retrying for $retry time, because cannot register cell: $t")
        if(retry < 3)
          initCell(rec, retry +1)
        else
          rec.logger.warn(s"Cannot register cell for maximal number of retries")
    }
    rec
  }
}


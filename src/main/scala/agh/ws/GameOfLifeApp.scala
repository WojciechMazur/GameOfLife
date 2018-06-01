package agh.ws

import agh.ws.actors.Cell.{ChangeStatus, Position, StatusChanged}
import agh.ws.actors.CellsManager.{CellRegistered, ChangeNeighbourhoodModel, NeighbourhoodModelChanged, RegisterCell}
import agh.ws.actors.{Cell, CellsManager}
import agh.ws.models.{CellRectangle, IterateButton}
import agh.ws.util._
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import org.slf4j.LoggerFactory

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scalafx.Includes._
import scalafx.application
import scalafx.application.{JFXApp, Platform}
import scalafx.beans.binding.Bindings
import scalafx.beans.property.StringProperty
import scalafx.beans.value.ObservableValue
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, ComboBox, Label, TextField}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox}
import scalafx.scene.paint.Color
import scalafx.util.StringConverter

object GameOfLifeApp extends JFXApp {
  val initCounter = new ObsersvableLongCounter()
  val iterationCounter = new ObsersvableLongCounter()
  val cellsX: Int = 500
  val cellsY: Int = 250
  val size = 3
  val spacing = 0
  val width =  cellsX*(size+spacing).toFloat
  val height = cellsY*(size+spacing).toFloat

  val initiallyAlive: StringProperty = StringProperty("0")

  val boundries: Boundries = Boundries(width, height)
  private val boundiresBehavior = BoundiresBehavior.Repetetive

  val cellsRectangles: mutable.HashMap[Long, CellRectangle] = mutable.HashMap[Long, CellRectangle]()
  val cellsRefs: mutable.HashMap[Long, ActorRef] = mutable.HashMap[Long, ActorRef]()
  val refsOfCells: mutable.HashMap[ActorRef, Long] = mutable.HashMap[ActorRef, Long]()
  val grainColors: mutable.HashMap[Long, Color] = mutable.HashMap(CellRectangle.noGrainGroup -> Color.LightGrey)

  implicit val system: ActorSystem = ActorSystem("Game-of-life")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout = 5.seconds
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  val log = LoggerFactory.getLogger(getClass.getName)

  val cellsManager: ActorRef = system.actorOf(CellsManager.props(boundries, boundiresBehavior, size + spacing, Moore), "manager")

  stage = new application.JFXApp.PrimaryStage {
    title.value = "Game of Life"
    scene = new Scene(width.toDouble, height.toDouble){
      val borderPane = new BorderPane()

      val initProgress = new Label{
      text <== Bindings.createStringBinding(
        () => s"Cells initialized: ${initCounter.get.value}/${cellsX*cellsY}   " +
          s"  ${(initCounter.get.value.toDouble / (cellsX * cellsY) *10000).toInt / 100.0}%",
        initCounter.get)
        padding = Insets(10, 5, 10, 5)
      }

      val iterationLabel = new Label{
        text <== Bindings.createStringBinding(
          () => s"Iteration: ${iterationCounter.get.value}",
          iterationCounter.get)
        padding = Insets(10, 5 , 10, 5)
      }
      val initiallyAliveTextField = new TextField{
        text = (cellsX*cellsY*0.1).toInt.toString
        padding = Insets(10, 5, 10, 5)
      }
      val neighbourhoodModelChoice = new ComboBox[NeighbourhoodModel](
        Seq(Moore, VonNeuman, HexagonalLeft, HexagonalRight, HexagonalRandom, PentagonalRandom)
      ) {
        converter = StringConverter.toStringConverter(_.name)
        padding = Insets(5)
        selectionModel.value.select(0)
        value.onChange[NeighbourhoodModel]{
          (o:ObservableValue[NeighbourhoodModel, NeighbourhoodModel], oldVal:NeighbourhoodModel, newVal:NeighbourhoodModel) =>
            changeNeighbourhoodModel(newVal)
        }
      }
      initiallyAlive <== initiallyAliveTextField.text
      val randomInitialyAliveButton: Button = new Button("Randomize alive"){
        onMouseClicked = {
          (_:MouseEvent) =>
            GrainsCounter.reset
            randomAliveCells
        }
        padding = Insets(10, 5, 10, 5)
      }
      val iterateButton = new IterateButton(cellsManager, refsOfCells, cellsRectangles){
        padding = Insets(10, 5, 10, 5)
      }

      borderPane.top = new HBox(iterateButton, initiallyAliveTextField, randomInitialyAliveButton, initProgress, iterationLabel, neighbourhoodModelChoice)
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
      cellsRectangles(refsOfCells(ref)).grainGroupId.value=CellRectangle.noGrainGroup
      ref ! ChangeStatus(0L, shouldReply = false)
      })
    randomizeCells(Set.empty, refs)

    @scala.annotation.tailrec
    def randomizeCells(ready: Set[ActorRef], rest: Set[ActorRef]): Unit = {
      import scala.util.Random
      ready.size match {
        case `maxCells` => ()
        case _ =>
          val groupId = GrainsCounter.inc
          val next = Random.nextInt(rest.size)
          val ref = rest.toVector(next)
          ref ? ChangeStatus(groupId) onComplete {
            case Success(StatusChanged(status, seedGroupId, _)) =>
              cellsRectangles(refsOfCells(ref)).grainGroupId.value = seedGroupId
            case Failure(t) => log.warn(t.toString)
            case Success(s) => log.warn(s"Unknown response $s")
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
        fill = grainColors(CellRectangle.noGrainGroup)
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
      case Success(s) => log.warn(s"Unknown response $s")
    }
  }

  private def changeNeighbourhoodModel(neighbourhoodModel: NeighbourhoodModel): Unit = {
    println("Changing neighbourhood model")
    cellsManager.ask(ChangeNeighbourhoodModel(neighbourhoodModel))(20.seconds) onComplete{
     case Success(NeighbourhoodModelChanged(_)) => println("Neighbourhood model changed successfully")
     case Failure(t) => println(t.toString)
     case other => println(s"Unknown response $other")
   }
  }
}


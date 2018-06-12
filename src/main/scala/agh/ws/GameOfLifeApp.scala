package agh.ws

import agh.ws.actors.Cell.{ChangeStatus, Position, StatusChanged}
import agh.ws.actors.CellsManager.{CellRegistered, ChangeNeighbourhoodModel, NeighbourhoodModelChanged, RegisterCell}
import agh.ws.actors.{Cell, CellsManager}
import agh.ws.core.cell.{CellRandomizer, DefaultRandomizer, EventlyDistributed}
import agh.ws.models.{CellRectangle, GrainGrowthButton}
import agh.ws.util.BoundiresBehavior.{Repetetive, Strict}
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
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.beans.value.ObservableValue
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.{Button, ComboBox, Label, TextField}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox, VBox}
import scalafx.scene.paint.Color
import scalafx.util.StringConverter

object GameOfLifeApp extends JFXApp {
  val initCounter = new ObsersvableLongCounter()
  val iterationCounter = new ObsersvableLongCounter()
  val cellsX: Int = 250
  val cellsY: Int = 250
  val size = 3
  val spacing = 0
  val width =  cellsX*(size+spacing).toFloat
  val height = cellsY*(size+spacing).toFloat

  val initiallyAlive: StringProperty = StringProperty("0")

  val boundries: Boundries = Boundries(width, height)
  private val boundiresBehaviorProperty = ObjectProperty[BoundiresBehavior](Repetetive)
  private val neighbourhoodModelProperty = ObjectProperty[NeighbourhoodModel](Moore)

  val cellsRectangles: mutable.HashMap[Long, CellRectangle] = mutable.HashMap[Long, CellRectangle]()
  val cellsRefs:   mutable.HashMap[Long, ActorRef] = mutable.HashMap[Long, ActorRef]()
  val refsOfCells: mutable.HashMap[ActorRef, Long] = mutable.HashMap[ActorRef, Long]()
  val grainColors: mutable.HashMap[Long, Color] = mutable.HashMap(CellRectangle.noGrainGroup -> Color.LightGrey)

  implicit val system: ActorSystem = ActorSystem("Game-of-life")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: akka.util.Timeout = 5.seconds
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  val log = LoggerFactory.getLogger(getClass.getName)

  val cellsManager: ActorRef = system.actorOf(CellsManager.props(boundries, boundiresBehaviorProperty.value, size + spacing, Moore), "manager")

  stage = new application.JFXApp.PrimaryStage {
    title.value = "Game of Life"
    scene = new Scene(width.toDouble, height.toDouble){
      val borderPane = new BorderPane()

      val initProgress = new Label{
      text <== Bindings.createStringBinding(
        () => s"Cells initialized: ${initCounter.get.value}/${cellsX*cellsY}   " +
          s"  ${(initCounter.get.value.toDouble / (cellsX * cellsY) *10000).toInt / 100.0}%",
        initCounter.get)
      padding=Insets(2.5f)
      }

      val iterationLabel = new Label {
        text <== Bindings.createStringBinding(
          () => s"Iteration: ${iterationCounter.get.value}",
          iterationCounter.get)
        padding = Insets(2.5f)
      }
      val initiallyAliveTextField = new TextField{
        maxWidth = 75f
        text = (cellsX*cellsY*0.1).toInt.toString
      }
      val neighbourhoodModelChoice = new ComboBox[NeighbourhoodModel](
        Seq(Moore, VonNeuman, HexagonalLeft, HexagonalRight, HexagonalRandom, PentagonalRandom)
      ) {
        converter = StringConverter.toStringConverter(_.name)
        selectionModel.value.select(0)
        value.onChange[NeighbourhoodModel]{
          (o:ObservableValue[NeighbourhoodModel, NeighbourhoodModel], oldVal:NeighbourhoodModel, newVal:NeighbourhoodModel) =>
            neighbourhoodModelProperty.value = newVal
            changeNeighbourhoodModel()
        }
      }

      val cellRandomizerChoice = new ComboBox[CellRandomizer](
        Seq(DefaultRandomizer, EventlyDistributed)
      ){
        converter = StringConverter.toStringConverter(_.name)
        selectionModel.value.select(0)

        }

      val boundiresBehaviorChoice = new ComboBox[BoundiresBehavior](
        Seq(Repetetive, Strict)
      ){
        converter = StringConverter.toStringConverter(_.name)
        selectionModel.value.select(0)
        value.onChange[BoundiresBehavior]{
          (o:ObservableValue[BoundiresBehavior, BoundiresBehavior], oldVal:BoundiresBehavior, newVal:BoundiresBehavior) =>
            boundiresBehaviorProperty.value = newVal
            changeNeighbourhoodModel()
        }
      }

      initiallyAlive <== initiallyAliveTextField.text
      val randomInitialyAliveButton: Button = new Button("Randomize"){
        onMouseClicked = {
          (_:MouseEvent) =>
            GrainsCounter.reset
            iterationCounter.reset
            cellRandomizerChoice.value.value.randomize(
              initiallyAlive.value.toLong,
              cellsRectangles.toMap,
              refsOfCells.toMap
            )
            cellRandomizerChoice.value.value.randomize(
              initiallyAlive.value.toLong,
              cellsRectangles.toMap,
              refsOfCells.toMap
            )
        }
      }
      val iterateButton = new GrainGrowthButton(cellsManager, refsOfCells, cellsRectangles)

      borderPane.left = new VBox(
        initProgress,
        new HBox(
          new Label("Initially alive: "){padding=Insets(2.5f)}, initiallyAliveTextField, randomInitialyAliveButton){padding = Insets(10, 5, 0, 5)},
        new HBox(new Label("Randomization method: "){padding=Insets(2.5f)}, cellRandomizerChoice){padding = Insets(5)},
        new HBox(new Label("Neighbourhood model:  "){padding=Insets(2.5f)}, neighbourhoodModelChoice){padding = Insets(5)},
        new HBox(new Label("Boundaries behavior:  "){padding=Insets(2.5f)}, boundiresBehaviorChoice){padding = Insets(5)},
        new HBox(iterateButton, iterationLabel){padding = Insets(10, 5, 10, 5)}
      ){
        padding = Insets(20, 5, 10, 10)
      }
      val cellsPane = new AnchorPane()
      cellsPane.children = createCells
      borderPane.center = cellsPane
      root = borderPane
    }
    onCloseRequest = {() =>
      GameOfLifeApp.system.terminate()
      Platform.exit()}
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

  private def changeNeighbourhoodModel(): Unit = {
    println("Changing neighbourhood model")
    val neighbourhoodModel = neighbourhoodModelProperty.value
    val boundiresBehavior  = boundiresBehaviorProperty.value

    cellsManager.ask(ChangeNeighbourhoodModel(neighbourhoodModel, boundiresBehavior))(20.seconds) onComplete{
     case Success(NeighbourhoodModelChanged(_)) => println(s"Neighbourhood model changed successfull to ${neighbourhoodModel.name} with ${boundiresBehavior.name} behavior")
     case Failure(t) => println(t.toString)
     case other => println(s"Unknown response $other")
   }
  }
}


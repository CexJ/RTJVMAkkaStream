package section4_advanced_akka_streams

import java.util.Random

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{ActorMaterializer, Attributes, Inlet, Outlet, SinkShape, SourceShape}

import scala.collection.mutable

object Lec04_TheGraphStageAPI extends App {

  implicit val system = ActorSystem("Lec04_TheGraphStageAPI")
  implicit val materializer = ActorMaterializer()

  /**
   * Custom source that emit random number
   */

  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]]{

    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    override def shape: SourceShape[Int] = SourceShape(outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // implement logic here
        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = {
            val nextNumber = random.nextInt(max)
            push(outPort, nextNumber)
          }
        })
      }
  }

  val randomNumberGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  randomNumberGeneratorSource.runWith(Sink.foreach(println))

  /**
   * custom sink that print in batchese
   */

  class BatchesPrinter(size: Int) extends GraphStage[SinkShape[Int]]{

    val inPort = Inlet[Int]("batchesPrinter")

    override def shape: SinkShape[Int] = SinkShape(inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        // implement logic here


        override def preStart(): Unit = {
          super.preStart()
          pull(inPort)
        }

        val batch = new mutable.Queue[Int]

        setHandler(inPort, new InHandler{

          override def onPush(): Unit = {
            val nextValue = grab(inPort)
            batch.enqueue(nextValue)
            if(batch.size >= size){
              println("New batch: "+ batch.dequeueAll(_ => true).mkString(","))
            }

            pull(inPort) // send demand
          }

          override def onUpstreamFinish(): Unit = {
            if(batch.nonEmpty) {
              println("New batch: "+ batch.dequeueAll(_ => true).mkString(","))
            }
            super.onUpstreamFinish()
          }
        })

      }
    }
  }

  val sinkBatch = Sink.fromGraph(new BatchesPrinter(5))
  randomNumberGeneratorSource.runWith(sinkBatch)
}

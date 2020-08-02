package section2_akka_streams_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Source}

object Lec05_GraphCycles extends App {

  implicit val system = ActorSystem("Lec05_GraphCycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 1000))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
                   mergeShape <~ incrementerShape

    ClosedShape
  }

  RunnableGraph.fromGraph(accelerator).run() // it stop after accelerating 1
  // graph cycle deadlock by backpressure

  /*
  solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 1000))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  RunnableGraph.fromGraph(accelerator).run()

  /*
  solution 2: buffer
 */

  val bufferAccelerator = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 1000))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape.preferred <~ repeaterShape

    ClosedShape
  }

  RunnableGraph.fromGraph(accelerator).run()
}

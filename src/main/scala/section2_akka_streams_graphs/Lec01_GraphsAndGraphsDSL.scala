package section2_akka_streams_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, Zip}

object Lec01_GraphsAndGraphsDSL extends App {

  implicit val system = ActorSystem("Lec01_GraphsAndGraphsDSL")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(_+1)
  val multiplier = Flow[Int].map(_*10)
  // I would like to execute them in parallel and merge them later
  val output = Sink.foreach[(Int, Int)](println)

  // 1) setting up fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE structure
      import GraphDSL.Implicits._// brings some nice operators in the scope

      // 2) add shapes
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // 3) tying up the shapes
      input ~> broadcast
      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1
      zip.out ~> output

      // 4) return closed shape
      ClosedShape // FREEZE the builder
      // shape
    } // graph
  ) // runnable graph

  graph.run()
}

package section1_akka_streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object Lec02_MaterializingStreams extends App {

  /**
   * extract meaningful values from a akka stream
   *
   * val result = graph.run()
   * run creates resources, actors... and execute them and create the result
   * the materializer has to construct all components of a graph that is why he need an ActorRefFactory
   *
   * each component produce a materialized value is up to us to decide which one is the result of the graph
   *
   * components are reusable but different run = different materializations
   */

  implicit val system = ActorSystem("Lec02_MaterializingStreams")
  implicit val materializer = ActorMaterializer()

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  val simpleMaterializedValue = simpleGraph.run() // it is NotUsed (basically unit)

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a, b) => a + b) // Sink[Int, Future[Int]] it will materialize in a Future[Int], the sum of the values
  val sumFuture = source.runWith(sink)

  import system.dispatcher
  sumFuture.onComplete{
    case Success(value) => println(s"The sum of all elements: $value")
    case Failure(ex) => println(s"I failed cause $ex")
  }

  // the left most materialized value is the default one

  // chooseng materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleSink = Sink.foreach[Int](println)

  simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)

  simpleSource.viaMat(simpleFlow)(Keep.right)
  simpleSource.viaMat(simpleFlow)(Keep.left)
  simpleSource.viaMat(simpleFlow)(Keep.both)
  simpleSource.viaMat(simpleFlow)(Keep.none)

  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right) // materialize in Future[Done] that triggers when the stream is closed

  graph.run().onComplete {
    case Success(_) => println("The stream is done")
    case Failure(_) => println("The stream fail")
  }


  // sugars
  Source(1 to 10).runWith(Sink.reduce[Int](_+_)) // source.toMat(Sink.reduce)(Keep.right).run()
  Source(1 to 10).runReduce[Int](_ + _) // same

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42))
  Flow[Int].map(_*2).runWith(simpleSource, simpleSink)


}

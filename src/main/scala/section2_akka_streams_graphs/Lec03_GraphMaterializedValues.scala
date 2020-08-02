package section2_akka_streams_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.util.{Failure, Success}

object Lec03_GraphMaterializedValues extends App {

  implicit val system = ActorSystem("Lec03_GraphMaterializedValues")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("Akka","is", "awesome","rock","the","jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0){(count, _) => count+1}

  /*
   * A composite component (sink)
   * - prints out all strings which are lowercase
   * - COUNTS the strings that are short (< 5 chars)
   *
   */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter) { implicit builder => counterShape => // add the parameter counter,  the function now is a HOF
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowerCaseFilter = builder.add(Flow[String].filter(w => w == w.toLowerCase()))
      val shortStringFilter = builder.add(Flow[String].filter(w => w.length < 5))

      broadcast.out(0) ~> lowerCaseFilter ~> printer
      broadcast.out(1) ~> shortStringFilter ~> counterShape // we use the shape we passed !!!

      SinkShape(broadcast.in)
    }
  )

  val future = wordSource.toMat(complexWordSink)(Keep.right).run()

  import system.dispatcher
  future.onComplete{
    case Success(count) => println(s"The total number of short strings is: $count")
    case Failure(ex) => println(s"The count fail cause: $ex")
  }


  val multipleComplexWordSink = Sink.fromGraph(
    GraphDSL.create(printer, counter) ((printerMatValue, counterMatValue) => counterMatValue) {
      implicit builder => (printerShape, counterShape) =>  // add the parameter counter,  the function now is a HOF
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[String](2))
      val lowerCaseFilter = builder.add(Flow[String].filter(w => w == w.toLowerCase()))
      val shortStringFilter = builder.add(Flow[String].filter(w => w.length < 5))

      broadcast.out(0) ~> lowerCaseFilter ~> printerShape // we use the shape we passed !!!
      broadcast.out(1) ~> shortStringFilter ~> counterShape // we use the shape we passed !!!

      SinkShape(broadcast.in)
    }
  )

}

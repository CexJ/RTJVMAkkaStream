package section1_akka_streams

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object Lec04_Backpressure extends App {

  /**
   * elements flow as demands from consumer
   *
   * backpressure is about synchronization of speed between async components
   *
   * fast consumer is good
   * slow consumer: problem => consumer communicate to the producer to slowdown
   * this protocol is called backpressure and can be controlled programmatically
   */


  implicit val system = ActorSystem("Lec04_Backpressure")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  fastSource.to(slowSink).run() // fusion, not backpressure

  fastSource.async
    .to(slowSink).run() // backpressure

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x+1
  }

  fastSource.async
    .via(simpleFlow).async // it buffering elements until he has to send a backpressure signal to the source
    .to(slowSink).run() // backpressure

  /** react to backpressure signal:
   * - try to slow down
   * - buffer elements
   * - dropdown elements if overflow
   * - tear down the whole stream
   */

  val bufferFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropTail)
  simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropNew)
  simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropBuffer)
  simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.backpressure)
  simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.fail)


  fastSource.async
    .via(bufferFlow).async
    .to(slowSink).run()
  // the first 16 are buffered by sink, to last 10 are buffered by flow (the oldest elements are dropped)

  // throttling
  import scala.concurrent.duration._
  fastSource.throttle(2, 1 second).runWith(Sink.foreach(println))
}

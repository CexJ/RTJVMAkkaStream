package section1_akka_streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object Lec03_OperatorFusionAndAsyncBoundaries extends App {

  implicit val system = ActorSystem("Lec03_OperatorFusionAndAsyncBoundaries")
  implicit val materializer = ActorMaterializer()

  val simpleSource = Source(1 to 1000)
  val simpleFlow = Flow[Int].map(_ + 1)
  val simpleFlow2 = Flow[Int].map(_ * 10)
  val simpleSink = Sink.foreach[Int](println)

  // this run in the SAME ACTOR
  simpleSource via simpleFlow via simpleFlow2 to simpleSink run
  // operator FUSION
  // it reduce overhead of message's passing

  // it not always the best choice

  val complexFlow = Flow[Int].map { x =>
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map { x =>
    Thread.sleep(1000)
    x*10
  }

  simpleSource via complexFlow via complexFlow2 to simpleSink run
  // it is very slow cause the same actor is waiting

  simpleSource.via(complexFlow).async
    .via(complexFlow2).async
    .to(simpleSink).run

  // ordering guarantees
  Source(1 to 3)
    .map(el => {println(s"Flow A: $el"); el})
    .map(el => {println(s"Flow B: $el"); el})
    .map(el => {println(s"Flow C: $el"); el})
    .runWith(Sink.ignore)
  // A1 B1 C1, A2 B2 C2, A3 B3 C3

  Source(1 to 3)
    .map(el => {println(s"Flow A: $el"); el}).async
    .map(el => {println(s"Flow B: $el"); el}).async
    .map(el => {println(s"Flow C: $el"); el})
    .runWith(Sink.ignore)

  // only A* < B* < C* not the total order
}

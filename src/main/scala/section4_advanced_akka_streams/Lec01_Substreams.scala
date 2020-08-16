package section4_advanced_akka_streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Lec01_Substreams extends App {

  implicit val system = ActorSystem("Lec01_Substreams")
  implicit val materializer = ActorMaterializer()

  /**
   * grouping streams by a certain function
   */
  val wordsSource = Source(List("Akka", "is", "amazing","learning","substreams"))
  val groups = wordsSource.groupBy(30, word => if(word.isEmpty) '\0' else word.toLowerCase().charAt(0))
  groups.to(Sink.fold(0)((count, word) => {
    val newCount = count +1
    println(s"I just received $word, count is $newCount")
    newCount
  })).run() // for each substream a sink is created

  /**
   * merge substreams back to a master stream
   */

  val textSource = Source(List(
    "I love akka stream",
    "this is amazing",
    "learning from rtjvm"
  ))

  val futureCount = textSource.groupBy(2, _.length%2)
    .map(_.length)
    .mergeSubstreamsWithParallelism(2)
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  import system.dispatcher
  futureCount.onComplete{
    case Success(value) => println(s"Total count: $value")
    case Failure(_) =>
  }

  /**
   * splitting a stream when a condition is met
   */

  val text ="""
      |I love akka stream
      |this is amazing
      |learning from rtjvm
      |""".stripMargin

  val altFutureCount = Source(text.toList)
    .splitWhen(_ == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  altFutureCount.onComplete{
    case Success(value) => println(s"Total count: $value")
    case Failure(_) =>
  }

  /**
   * flattening streams
   */

  val simpleSource = Source(1 to 5)
  simpleSource.flatMapConcat(i => Source(i to i*3)).runWith(Sink.foreach(println))

  simpleSource.flatMapMerge(2, i => Source(i to i*3)).runWith(Sink.foreach(println))
}

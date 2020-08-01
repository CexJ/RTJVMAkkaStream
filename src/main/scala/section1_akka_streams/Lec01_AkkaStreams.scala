package section1_akka_streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object Lec01_AkkaStreams extends App {
  /**
   * - publisher: emits elements
   * - subscriber: receives elements
   * - processor: transforms elements
   *
   * done in asynchronous way
   * backpressure
   *
   * reactive streams: SPI (service provider interface) not API
   */


  /**
   * Source: publisher
   * Sink: subscriber (terminate if subscriber terminate)
   * Flow: processor
   */

  /**
   * Upstream (to the source)
   * Downstream (to the sink)
   */

  implicit val system = ActorSystem("Lec01_AkkaStreams")
  // it needs an implicit ActorRefFactory
  implicit val materializer = ActorMaterializer()

  //source
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)

  //graph
  val graph = source to sink

  // running graph
  // it needs a implicit materializer
  graph.run

  // flows
  val flow = Flow[Int].map(_ + 1)

  val sourceWithFlow = source via flow

  val sinkWithFlow = flow to sink

  sourceWithFlow to sink run

  source to sinkWithFlow run

  source via flow to sink run

  // nulls are NOT allowed
  val illegalSource = Source.single[String](null)
  illegalSource to Sink.foreach(println) run // exception

  // USE OPTION instead


  /**
   * Sources
   */

  // empty source
  val emptySource = Source.empty[Int]
  // single source
  val singleSource = Source.single(1)
  // finite source
  val finalSource = Source(List(1,2,3))
  // infinite source
  val infiniteSource = Source(Stream.from(1)) // do not confuse akka stream with collection stream

//  import scala.concurrent.ExecutionContext.Implicits.global
  import system.dispatcher
  // future source
  val futureSource = Source.fromFuture(Future(42))

  // sinks
  val ignoreSink = Sink.ignore
  // foreach sink
  val foreachSink = Sink.foreach[String](println)
  // retrieve value
  // head sink
  val headSink = Sink.head[Int]
  // fold sink
  val foldSink = Sink.fold[Int, Int](0)((a,b) => a + b)

  // flows
  // map flow
  val mapFlow = Flow[Int].map(2*_)
  // take flow
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT flatmap


  // source -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source via mapFlow via takeFlow to sink

  // syntactic sugar
  val mapSource = Source(1 to 10).map(2*_) // same of Source via flow.map

  // run stream directly
  mapSource.runForeach(println) // same of mapSource to Sink.foreach

  // OPERATORS = components
}

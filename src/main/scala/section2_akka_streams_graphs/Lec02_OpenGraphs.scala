package section2_akka_streams_graphs

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source, ZipWith}
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, FlowShape, SinkShape, SourceShape, UniformFanInShape}

object Lec02_OpenGraphs extends App {

  implicit val system = ActorSystem("Lec02_OpenGraphs")

  implicit val materializer = ActorMaterializer()


  /*
  A composite source that concatenates 2 sources
  - emits ALL the elements from the first source
  - then ALL the elements from the second source
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(1 to 10)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._
      val concat = builder.add(Concat[Int](2))
      firstSource ~> concat // magical implicit conversion component to shape
      secondSource ~> concat

      SourceShape(concat.out) // shape
    } // static graph
  ) // component

  sourceGraph.to(Sink.foreach(println))

  /*
  Complex sink
   */

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      broadcast ~> sink1 // magical implicit conversion component to shape
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  Source(1 to 1000).runWith(sinkGraph)

  /*
  Complex flow
   */

  val incrementer = Flow[Int].map(_ +1)
  val multiplier = Flow[Int].map(_ *10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._

      // shapes are not components you must create a shape from a component

      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)
      incrementerShape ~> multiplierShape
      // magical implicit conversion component to shape DOESN'T work cause it should converse both

      FlowShape(incrementerShape.in, multiplierShape.out)
    }
  )

  Source(1 to 1000).runWith(sinkGraph)

  /**
   * a flow composed by Sink followed by Source can be construct in this way but it would have problems:
   * - Stream termination
   * - backpressure
   *
   * akka stream has its version
   * Flow.fromSinkAndSourceCoupled
   */

  /*
  Max3 operator
  - 3 input of type Int
  - the maximum of the 3
   */

  val max3StaticGraph = GraphDSL.create() {implicit builder =>

    import GraphDSL.Implicits._

    val max1 = builder.add(ZipWith[Int, Int, Int]((a,b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a,b) => Math.max(a, b)))
    max1.out ~> max2.in0
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](println)

  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val max3Shape = builder.add(max3StaticGraph)

      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape ~> maxSink

      ClosedShape
    }
  )

  max3RunnableGraph.run()

  // same for UniformFanOutShape

  /*
  Non-uniform fan out shape

  processing bank transactions
  txn suspicious if amount > 10000

  Streams component ofr txns
  - output1: let the transaction go through
  - output2: suspicious txn ids
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)
  val transactionSource = Source(List(
    Transaction("65498498561", "Paul", "Jim", 100, new Date()),
    Transaction("98798798989", "Daniel", "Jim", 1000000, new Date()),
    Transaction("89728654981", "Jim", "Alice", 7000, new Date())
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](txnId => println(s"Suspicious transaction ID: $txnId"))

  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>

    import GraphDSL.Implicits._

    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousFilter = builder.add(Flow[Transaction].filter(_.amount >10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](_.id))

    broadcast.out(0) ~> suspiciousFilter ~> txnIdExtractor
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph {
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._

      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      transactionSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService

      ClosedShape

    }
  }

  suspiciousTxnRunnableGraph.run()
}

package section4_advanced_akka_streams

import akka.actor.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Inlet, Outlet, Shape}

import scala.collection.immutable

object Lec03_CustomGraphShapes extends App {

  implicit val system = ActorSystem("Lec03_CustomGraphShapes")
  implicit val materializer = ActorMaterializer()

  // balance 2x3 shape
  case class Balance2x3 (
    in0: Inlet[Int], in1: Inlet[Int],
    out0:Outlet[Int], out1:Outlet[Int], out2:Outlet[Int])
    extends Shape {

    override val inlets: immutable.Seq[Inlet[_]] = List(in0, in1)

    override val outlets: immutable.Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(), in1.carbonCopy(),
      out0.carbonCopy(), out1.carbonCopy(), out2.carbonCopy())
  }

  val balance2x3Impl = GraphDSL.create(){ implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))
    merge ~> balance

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2))
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      import scala.concurrent.duration._
      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1 second)

      def createSink(index: Int) =
        Sink.fold(0)((count: Int, element: Int) => {
          println(s"Sink $index counting $element")
          count+1
      })

      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))
      val sink3 = builder.add(createSink(3))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1

      balance2x3.out0 ~> sink1
      balance2x3.out1 ~> sink2
      balance2x3.out2 ~> sink3

      ClosedShape
    }
  )
}

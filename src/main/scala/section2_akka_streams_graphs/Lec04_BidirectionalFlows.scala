package section2_akka_streams_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}

object Lec04_BidirectionalFlows extends App {

  implicit val system = ActorSystem("Lec04_BidirectionalFlows")
  implicit val materializer = ActorMaterializer()

  /*
  Example: cryptography
   */

  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)


  // bidiflow
  val bidiCryptoStaticGraph = GraphDSL.create() {implicit builder =>

    val encryptFlowShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptFlowShape = builder.add(Flow[String].map(decrypt(3)))

    BidiShape.fromFlows(encryptFlowShape, decryptFlowShape)
  }

  val unencryptedList = List("Akka","is", "awesome","rock","the","jvm")
  val unencryptedSource = Source(unencryptedList)
  val encryptedSource = Source(unencryptedList.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>

      import GraphDSL.Implicits._

      val unencryptedSourceShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)
      val bidiShape = builder.add(bidiCryptoStaticGraph)
      val encryptedSinkShape = builder.add((Sink.foreach[String](s => println(s"Encrypted: $s"))))
      val decryptedSinkShape = builder.add((Sink.foreach[String](s => println(s"Decrypted: $s"))))

      unencryptedSourceShape ~> bidiShape.in1 ; bidiShape.out1 ~> encryptedSinkShape.in
      decryptedSinkShape <~ bidiShape.out2 ; bidiShape.in2 <~ encryptedSourceShape.out

      ClosedShape
    }
  )

  cryptoBidiGraph.run()
}

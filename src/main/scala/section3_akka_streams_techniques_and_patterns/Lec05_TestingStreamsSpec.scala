package section3_akka_streams_techniques_and_patterns

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.pipe
import akka.stream.testkit.scaladsl.{TestSink, TestSource}

import scala.util.{Failure, Success}

class Lec05_TestingStreamsSpec
  extends TestKit(ActorSystem("Lec05_TestingStreamsSpec"))
  with WordSpecLike
  with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)
  import system.dispatcher

  "A simple string" should {
    "satisfy basic assertion" in {
      // describe the test
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a+b)

      val sumFuture = simpleSource.toMat(simpleSink)(Keep.right).run()
      val sum = Await.result(sumFuture, 2 seconds)
      assert(sum == 55)
    }

    "integrate with testActor via materialized value" in {
      val simpleSource = Source(1 to 10)
      val simpleSink = Sink.fold(0)((a: Int, b: Int) => a+b)

      val probe = TestProbe()
      simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(probe.ref)

      probe.expectMsg(55)
    }

    "integrate with a testActor based sink" in {
      val simpleSource = Source(1 to 5)
      val simpleFlow = Flow[Int].scan[Int](0)(_+_)
      val simpleSteam = simpleSource.via(simpleFlow)

      val probe = TestProbe()
      val probeSink = Sink.actorRef(probe.ref, "completion message")
      simpleSteam.to(probeSink).run()

      probe.expectMsgAllOf(0,1,3,6,10,15)
    }

    "integrate with Stream TestKit Sink" in {
      val sourceUnderTest =  Source(1 to 5).map(_ * 2)
      val testSink = TestSink.probe[Int]
      val materializedTestValue = sourceUnderTest.runWith(testSink)

      materializedTestValue
        .request(5)
        .expectNext(2,4, 6, 8, 10)
        .expectComplete()
    }

    "integrate with Stream TestKit Source" in {
      val sinkUnderTest =  Sink.foreach[Int]{
        case 13 => throw new RuntimeException("badluck")
        case _ =>
      }
      val testSource = TestSource.probe[Int]
      val materialized = testSource.toMat(sinkUnderTest)(Keep.both).run()
      val (testPublisher,resultFuture) = materialized

      testPublisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(13)
        .sendComplete()

      resultFuture.onComplete {
        case Success(_) => fail("The sink under test should have throw an exception")
        case Failure(_) =>
      }
    }

    "integrate with Stream TestKit Source and Stream TestKit Sink" in {
      val flowUnderTest = Flow[Int].map(_*2)
      val testSource = TestSource.probe[Int]
      val testSink = TestSink.probe[Int]

      val materialized = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
      val (publisher, subscriber) = materialized

      publisher
        .sendNext(1)
        .sendNext(5)
        .sendNext(42)
        .sendNext(99)
        .sendComplete()

      subscriber
        .request(4)
        .expectNext(2, 10, 84, 198)
        .expectComplete()
    }
  }
}

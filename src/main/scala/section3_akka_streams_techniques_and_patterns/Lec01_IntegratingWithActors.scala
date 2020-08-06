package section3_akka_streams_techniques_and_patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

object Lec01_IntegratingWithActors extends App {

  implicit val system = ActorSystem("Lec01_IntegratingWithActors")
  implicit val materializer = ActorMaterializer()

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Juest received a string $s")
        sender() ! s"$s$s"
      case n: Int =>
        log.info(s"Just received a number: $n")
      case _ =>
    }
  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  val numberSource = Source(1 to 10)

  // actor as flow
  import scala.concurrent.duration._
  implicit val timeout = Timeout(2 seconds)
  // actor as a flow
  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)
  // parallelism: how many messages can be in the mailbox before backpressure

  numberSource.via(actorBasedFlow).to(Sink.foreach[Int](println)).run()
  // or
  numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.foreach[Int](println)).run()

  // actor as a source
  val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy =  OverflowStrategy.dropHead)

  val materializedValue = actorPoweredSource.to(Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))).run()
  materializedValue ! 42
  // terminating the stream
  materializedValue ! akka.actor.Status.Success("complete")

  /*
   Actor as a sink
   - an init message
   - an ack message to confirm the reception
   - a complete message
   - a function to generate a message in case the stream throws an exception
   */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFail(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info("Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream complete")
        context.stop(self)
      case StreamFail(ex) =>
        log.warning(s"Stream fail: $ex")
      case message =>
        log.info(s"$message sink")
        sender() ! StreamAck
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")

  val actorPoweredSink = Sink.actorRefWithAck[Int](
    destinationActor,
    onInitMessage = StreamInit,
    onCompleteMessage = StreamComplete,
    ackMessage = StreamAck,
    //optional
    onFailureMessage = throwable => StreamFail(throwable)
  )

  numberSource.to(actorPoweredSink).run()

}

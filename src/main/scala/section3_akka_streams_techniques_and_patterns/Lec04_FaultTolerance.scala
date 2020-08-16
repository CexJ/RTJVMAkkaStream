package section3_akka_streams_techniques_and_patterns

import java.util.Random

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

object Lec04_FaultTolerance extends App {

  implicit val system = ActorSystem("Lec04_FaultTolerance")
  implicit val materializer = ActorMaterializer()

  val faultySource = Source(1 to 10).map{
    case 6 => throw new RuntimeException("It's an exception")
    case n => n
  }

  /**
   * 1 - logging
   */
  faultySource.log("tracking elements")
    .to(Sink.ignore)
    .run()
  // debug for elements errors for exceptions

  /**
   * 2 - Gracefully terminate a stream
   */
  faultySource.recover{
    case _: RuntimeException => Int.MinValue
  }.log("gracefully")
    .to(Sink.ignore)
    .run()
  // the stream is stopped but does not crash

  /**
   * 3 - recover with another stream
   */


  faultySource.recoverWithRetries(3, { // it retries to replace it 3 times
    case _: RuntimeException => Source(90 to 99)
  }).log("with another stream")
    .to(Sink.ignore)
    .run()

  /**
   * 4 - backoff supervision
   */
  import scala.concurrent.duration._
  val restartSource = RestartSource.onFailuresWithBackoff(
    minBackoff = 1 second,
    maxBackoff = 30 seconds,
    randomFactor = 0.2
  )(() => {
    val randomNumber = new Random().nextInt(20)
    Source(1 to 10).map(elem=> if (elem == randomNumber) throw new RuntimeException else elem)
  })

  restartSource.log("with backoff")
    .to(Sink.ignore)
    .run()

  /**
   * 5 - supervision strategy
   */

  // by default the supervision strategy is just fail and terminate
  val numbers = Source(1 to 20).map{
    case 13 => throw new RuntimeException
    case n => n
  }

  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy{
  /*
    Resume = skip the element
    Stop = stop the stream
    Restart = resume + clean internal state if any
  */
    case _: RuntimeException => Resume
    case _ => Stop
  })

  supervisedNumbers.log("with supervision")
    .to(Sink.ignore)
    .run()
}

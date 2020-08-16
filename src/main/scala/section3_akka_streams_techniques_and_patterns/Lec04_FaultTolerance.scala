package section3_akka_streams_techniques_and_patterns

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Lec04_FaultTolerance extends App {

  implicit val system = ActorSystem("Lec04_FaultTolerance")
  implicit val materializer = ActorMaterializer()

}

package section3_akka_streams_techniques_and_patterns

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future

object Lec02_IntegratingWithExternalServices extends App {

  implicit val system = ActorSystem("Lec02_IntegratingWithExternalServices")
  implicit val materializer = ActorMaterializer()

  def genericExtService[A,B](data: A): Future[B] = ???

  // example: simplified pagerDuty

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "Service stop to respond", new Date),
    PagerEvent("SuperFrontEnd", "A button broke", new Date)
  ))

  object PagerService {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val map = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Lady Gaga" -> "lady.gaga@rtjvm.com"
    )

    //import system.dispatcher
    // but you should use a dedicated dispatcher to not starve the system one

    implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
    def processEvent(event: PagerEvent) = Future {
      val engineerIndex = (event.date.toInstant.toEpochMilli/(24*3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val email = map(engineer)
      println(s"Sending engineer $email")

      Thread.sleep(1000)
      email
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineerEvent = infraEvents.mapAsync(parallelism = 4)(PagerService.processEvent)
  // guarantees the relative order
  // mapAsyncUnordered

  val pageEmailSink = Sink.foreach[String](email => s"Successfully sent notification $email")

  pagedEngineerEvent.to(pageEmailSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val map = Map(
      "Daniel" -> "daniel@rtjvm.com",
      "John" -> "john@rtjvm.com",
      "Lady Gaga" -> "lady.gaga@rtjvm.com"
    )

    //import system.dispatcher
    // but you should use a dedicated dispatcher to not starve the system one

    implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
    private def processEvent(event: PagerEvent) = {
      val engineerIndex = (event.date.toInstant.toEpochMilli/(24*3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val email = map(engineer)
      log.info(s"Sending engineer $email")

      Thread.sleep(1000)
      email
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! pagerEvent
    }
  }

  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  import scala.concurrent.duration._
  implicit val timeout = Timeout(3 seconds)
  val altEngEmail = infraEvents.mapAsync(4)(event => (pagerActor ? event).mapTo[String])
  altEngEmail.to(pageEmailSink).run()

}

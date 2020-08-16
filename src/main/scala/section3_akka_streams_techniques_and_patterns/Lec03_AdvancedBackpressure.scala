package section3_akka_streams_techniques_and_patterns

import java.util.Date

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object Lec03_AdvancedBackpressure extends App {

  implicit val system = ActorSystem("Lec03_AdvancedBackpressure")
  implicit val materializer = ActorMaterializer()

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Server discovery fail", new Date()),
    PagerEvent("Illegal element in the pipeline", new Date()),
    PagerEvent("Number of HTTP 500 spiked", new Date())
  )
  val eventSource = Source(events)
  val onCallEngineer = "daniel@rtjvm.com"

  def sendEmail(notification: Notification) =
    println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")

  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  eventSource.to(notificationSink).run

  // if notificationSink is slow try to backpressure but eventSource could not respond to backpressure

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email} you have an event ${notification.pagerEvent}")
  }

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate((ev1, ev2) => {
      val nInstances = ev1.nInstances + ev2.nInstances
      PagerEvent(s"You have $nInstances events that require your attention", new Date, nInstances)
    })
    .map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  eventSource
    .via(aggregateNotificationFlow).async // to break into different actors
    .to(Sink.foreach[Notification](sendEmailSlow)).run()

  /*
    slow producer
   */

  import scala.concurrent.duration._
  val slowCounter = Source(Stream.from(1)).throttle(1,1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))

  slowCounter.via(extrapolator).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element)) // create at any time
}

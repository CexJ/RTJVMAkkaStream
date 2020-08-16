package section4_advanced_akka_streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

object Lec02_DynamicStreamHandling extends App {

  implicit val system = ActorSystem("Lec02_DynamicStreamHandling")
  implicit val materializer = ActorMaterializer()

  /**
   * Kill switch
   */

  val killSwitchFlow = KillSwitches.single[Int] // it materialize to a special value

  import scala.concurrent.duration._
  val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
  val simpleSink = Sink.ignore

  val killSwitch = counter.viaMat(killSwitchFlow)(Keep.right).to(simpleSink).run()

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds){
    killSwitch.shutdown()
  }

  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anothercounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)

  import system.dispatcher
  system.scheduler.scheduleOnce(3 seconds){
    sharedKillSwitch.shutdown()
  }

  /**
   * merge hub
   */

  val dynamicMerge = MergeHub.source[Int]
  val materializedSink = dynamicMerge.to(Sink.foreach[Int](println)).run()

 Source(1 to 10).runWith(materializedSink)
 counter.runWith(materializedSink) // always the same consumer

  /**
   * broadcast hub
   */

  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 10).runWith(dynamicBroadcast)

  materializedSource.runWith(Sink.ignore)
  materializedSource.runWith(Sink.foreach(println))

}

package com.github.takezoe.dgit.controller

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    //val config = Config.load()
    Resty.register(new APIController())

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props[CheckRepositoryNodeActor]), "tick")

  }

}

class CheckRepositoryNodeActor() extends Actor {
  override def receive = {
    case _ => {
      val timeout = System.currentTimeMillis() - (5 * 60 * 1000)
      Nodes.all().foreach { node =>
        Nodes.timestamp(node).foreach { timestamp =>
          if(timestamp < timeout){
            println(node + " is retired.") // TODO
            Nodes.remove(node)
          }
        }
      }
    }
  }
}

case class Result(result: String)
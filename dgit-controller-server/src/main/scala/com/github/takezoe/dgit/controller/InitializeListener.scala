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
    val config = Config(2, 0.9d)

    Resty.register(new APIController(config))

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props[CheckRepositoryNodeActor]), "tick")

  }

}

class CheckRepositoryNodeActor() extends Actor {
  override def receive = {
    case _ => {
      val timeout = System.currentTimeMillis() - (5 * 60 * 1000)
      Nodes.allNodes().foreach { case (endpoint, status) =>
        if(status.timestamp < timeout){
          println(endpoint + " is retired.") // TODO
          Nodes.removeNode(endpoint)
        }
      }
    }
  }
}

case class Result(result: String)
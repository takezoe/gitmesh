package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.dgit.repository.models.Node
import com.github.takezoe.resty._
import akka.actor._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()
    Resty.register(new APIController(config))

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props[HeartBeatActor]), "tick")
  }

}

class HeartBeatActor extends Actor with HttpClientSupport {

  override def receive: Receive = {
    case _ => {
      val config = Config.load()
      val dir = new File(config.dir)
      val diskUsage = dir.getFreeSpace.toDouble / dir.getTotalSpace.toDouble
      val repos = dir.listFiles(_.isDirectory).toSeq.map(_.getName)

      httpPostJson[String](
        config.controllerUrl + "/api/nodes/join",
        Node("http://localhost:8081", diskUsage, repos)
      ) match {
        case Right(_) => // success
        case Left(e) => println(e.errors)
      }
    }
  }

}

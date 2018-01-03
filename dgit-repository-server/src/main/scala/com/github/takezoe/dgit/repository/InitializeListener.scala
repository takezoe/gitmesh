package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import akka.actor._
import akka.event.Logging
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

import scala.concurrent.Await
import scala.concurrent.duration._

@WebListener
class InitializeListener extends ServletContextListener {

  private val system = ActorSystem("mySystem")


  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()
    Resty.register(new APIController(config))

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[HeartBeatActor], config)), "tick")
  }

}

class HeartBeatActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive: Receive = {
    case _ => {
      val roorDir = new File(config.directory)
      val diskUsage = roorDir.getFreeSpace.toDouble / roorDir.getTotalSpace.toDouble
      val repos = roorDir.listFiles(_.isDirectory).toSeq.map(_.getName)

      httpPostJson[String](
        s"${config.controllerUrl}/api/nodes/join",
        Node("http://localhost:8081", diskUsage, repos)
      ) match {
        case Right(_) => // success
        case Left(e) => log.error(e.errors.mkString("\n"))
      }
    }
  }

}

case class Node(node: String, diskUsage: Double, repos: Seq[String])
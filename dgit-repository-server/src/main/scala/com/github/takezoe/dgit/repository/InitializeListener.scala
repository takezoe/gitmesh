package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import akka.actor._
import akka.event.Logging
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.apache.commons.io.FileUtils

import scala.concurrent.Await
import scala.concurrent.duration._
import syntax._

@WebListener
class InitializeListener extends ServletContextListener {

  private val system = ActorSystem("mySystem")

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()
    Resty.register(new APIController()(config))

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[HeartBeatActor], config)), "tick")
  }

}

class HeartBeatActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive: Receive = {
    case _ => {
      val rootDir = new File(config.directory).unsafeTap { dir =>
        if(!dir.exists){
          dir.mkdirs()
        }
      }

      val diskUsage = 1.0d - (rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble)
      val repos = rootDir.listFiles(_.isDirectory).toSeq.map { dir =>
        val timestamp = FileUtils.readFileToString(new File(rootDir, s"${dir.getName}.id"), "UTF-8").toLong
        JoinNodeRepository(dir.getName, timestamp)
      }

      httpPostJson[String](
        s"${config.controllerUrl}/api/nodes/join",
        JoinNodeRequest(config.url, diskUsage, repos)
      ) match {
        case Right(_) => // success
        case Left(e) => log.error(e.errors.mkString("\n"))
      }
    }
  }

}

case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
case class JoinNodeRepository(name: String, timestamp: Long)
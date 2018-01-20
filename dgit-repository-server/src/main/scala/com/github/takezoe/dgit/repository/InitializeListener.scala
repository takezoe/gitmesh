package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import akka.actor._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

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

    defining(new File(config.directory)) { dir =>
      if(!dir.exists){
        dir.mkdirs()
      }
    }

    Resty.register(new APIController()(config))

    val notifier = new Notifier(config)
    //notifier.send()

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[NotifyActor], notifier)), "tick")
  }

}

class NotifyActor(notifier: Notifier) extends Actor with HttpClientSupport {

  override def receive: Receive = {
    case _ => notifier.send()
  }

}

class Notifier(config: Config) extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(classOf[Notifier])
  private val urls = config.controllerUrl.map { url => s"$url/api/nodes/notify" }
  // TODO Be configurable
  implicit override val httpClientConfig = Config.httpClientConfig.copy(maxFailure = 5, resetInterval = 5 * 60 * 1000)

  def send(): Unit = {
    val rootDir = new File(config.directory)
    val diskUsage = 1.0d - (rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble)
    val repos = rootDir.listFiles(_.isDirectory).toSeq.map { dir =>
      val timestamp = FileUtils.readFileToString(new File(rootDir, s"${dir.getName}.id"), "UTF-8").toLong
      JoinNodeRepository(dir.getName, timestamp)
    }

    httpPostJson[String](urls, JoinNodeRequest(config.url, diskUsage, repos)) match {
      case Right(_) => // success
      case Left(e) => log.error(e.errors.mkString("\n"))
    }
  }

}

case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
case class JoinNodeRepository(name: String, timestamp: Long)
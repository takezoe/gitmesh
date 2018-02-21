package com.github.takezoe.gitmesh.repository

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

    val heartBeatSender = new HeartBeatSender(config)
    //notifier.send()

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("heatBeat", system.actorOf(Props(classOf[HeartBeatActor], heartBeatSender)), "tick")
  }

}

class HeartBeatActor(notifier: HeartBeatSender) extends Actor with HttpClientSupport {

  override def receive: Receive = {
    case _ => notifier.send()
  }

}

class HeartBeatSender(config: Config) extends HttpClientSupport {

  implicit override val httpClientConfig = config.httpClient

  private val log = LoggerFactory.getLogger(classOf[HeartBeatSender])
  private val urls = config.controllerUrl.map { url => s"$url/api/nodes/notify" }

  def send(): Unit = {
    val rootDir = new File(config.directory)
    val diskUsage = 1.0d - (rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble)
    val repos = rootDir.listFiles(_.isDirectory).toSeq.map { dir =>
      val timestamp = FileUtils.readFileToString(new File(rootDir, s"${dir.getName}.id"), "UTF-8").toLong
      HeartBeatRepository(dir.getName, timestamp)
    }

    httpPostJson[String](urls, HeartBeatRequest(config.url, diskUsage, repos)) match {
      case Right(_) => // success
      case Left(e) => log.error(e.errors.mkString("\n"))
    }
  }

}

case class HeartBeatRequest(url: String, diskUsage: Double, repos: Seq[HeartBeatRepository])
case class HeartBeatRepository(name: String, timestamp: Long)
package com.github.takezoe.gitmesh.repository.servlet

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import akka.actor._
import cats.effect.IO

import scala.concurrent.duration._
import com.github.takezoe.gitmesh.repository.api.{Routes, Services}
import com.github.takezoe.gitmesh.repository.job.{HeartBeatActor, HeartBeatSender}
import com.github.takezoe.gitmesh.repository.util.Config
import com.github.takezoe.gitmesh.repository.util.syntax.defining
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import org.http4s.client.blaze.Http1Client
import org.http4s.server.middleware._
import org.http4s.servlet.syntax.ServletContextSyntax

import scala.concurrent.Await

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {

  private val system = ActorSystem("mySystem")
  private val httpClient = Http1Client[IO]().unsafeRunSync

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val context = sce.getServletContext
    implicit val config = Config.load()

    defining(new File(config.directory)) { dir =>
      if(!dir.exists){
        dir.mkdirs()
      }
    }

    val services = new Services(httpClient)
    context.mountService("gitmeshRepositoryService", CORS(Routes(services)))

    val heartBeatSender = new HeartBeatSender(httpClient, config)
    //notifier.send()

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("heatBeat", system.actorOf(Props(classOf[HeartBeatActor], heartBeatSender)), "tick")
  }

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)

    httpClient.shutdown.unsafeRunSync()
  }

}

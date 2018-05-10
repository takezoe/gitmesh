package com.github.takezoe.gitmesh.repository.servlet

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import akka.actor._
import cats.effect.IO
import com.github.takezoe.gitmesh.repository.api.{Routes, Services}
import com.github.takezoe.gitmesh.repository.job.{HeartBeatActor, HeartBeatSender}
import com.github.takezoe.gitmesh.repository.util.Config
import com.github.takezoe.gitmesh.repository.util.syntax.defining
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import fs2.Scheduler
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.server.middleware._
import org.http4s.servlet.syntax.ServletContextSyntax

import scala.concurrent.Await
import scala.concurrent.duration._
// TODO Don't use global executoon context!
import scala.concurrent.ExecutionContext.Implicits.global

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {

  private implicit val config = Config.load()
  private implicit val scheduler = Scheduler.allocate[IO](4).unsafeRunSync()._1

  private val system = ActorSystem("mySystem")

  private val httpClient = Retry[IO](RetryPolicy { i =>
    if(i > config.httpClient.maxRetry) None else Some(config.httpClient.retryInterval.milliseconds)
  })(Http1Client[IO](BlazeClientConfig.defaultConfig.copy(
    requestTimeout      = config.httpClient.requestTimeout.milliseconds,
    idleTimeout         = config.httpClient.requestTimeout.milliseconds,
    maxTotalConnections = config.httpClient.maxConnections,
    maxWaitQueueLimit   = config.httpClient.maxWaitQueue
    // TODO Use IO execution context instead of global one.
  )).unsafeRunSync)

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val context = sce.getServletContext

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

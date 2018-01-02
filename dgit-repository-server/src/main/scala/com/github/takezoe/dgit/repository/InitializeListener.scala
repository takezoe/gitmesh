package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.dgit.repository.models.Node
import com.github.takezoe.resty._
import com.github.takezoe.resty.util.JsonUtils
import okhttp3._
import akka.actor._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()
    Resty.register(new APIController(config))

//    // TODO Use Scala HTTP client?
//    println("Join to the cluster: " + config.controllerUrl) // TODO debug
//    val client = new OkHttpClient()
//    val request = new Request.Builder()
//      .url(config.controllerUrl + "/api/nodes/join")
//      .post(RequestBody.create(HttpClientSupport.ContentType_JSON, JsonUtils.serialize(Node("localhost", 8081)))) // TODO
//      .build()
//    val response = client.newCall(request).execute
//    println(response.body.string) // TOD debug

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props[HeartBeatActor]), "tick")
  }

}

class HeartBeatActor extends Actor {

  private val client = new OkHttpClient()

  override def receive: Receive = {
    case _ => {
      val config = Config.load()
      val dir = new File(config.dir)
      val diskUsage = dir.getFreeSpace.toDouble / dir.getTotalSpace.toDouble
      val repos = dir.listFiles(_.isDirectory).toSeq.map(_.getName)

      val request = new Request.Builder()
        .url(config.controllerUrl + "/api/nodes/join")
        .post(
          RequestBody.create(HttpClientSupport.ContentType_JSON,
          JsonUtils.serialize(Node("http://localhost:8081", diskUsage, repos)))
        ) // TODO
        .build()
      try {
        val response = client.newCall(request).execute
        println(response.body.string) // TODO debug
      } catch {
        case e: Exception => println(e.toString) // TODO debug
      }
    }
  }

}

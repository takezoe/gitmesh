package com.github.takezoe.dgit.controller

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._
import com.github.takezoe.resty.util.JsonUtils
import okhttp3.{OkHttpClient, Request, RequestBody}

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    //val config = Config.load()
    val nodes = new Nodes()
    Resty.register(new APIController(nodes))

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props[CheckRepositoryNodeActor]), nodes)
  }

}

class CheckRepositoryNodeActor() extends Actor {
  override def receive = {
    case nodes: Nodes => {
      val client = new OkHttpClient()

      nodes.all().foreach { node =>
        val url = "http://" + node.host + ":" + node.port + "/api/healthCheck"
        println("check: " + url)

        val request = new Request.Builder().url(url).build()

        try {
          val response = client.newCall(request).execute
          val result = JsonUtils.deserialize(response.body.string, classOf[Result]).asInstanceOf[Result]
          if(result.result != "OK"){
            println("[WARN] " + node + " is not alive!")
            println(result)
          } else {
            println(node + " is alive!")
          }
        } catch {
          case e: Exception =>
            println("[WARN] " + node + " is not alive!")
            println(e.toString)
        }
      }
    }
  }
}

case class Result(result: String)
package com.github.takezoe.dgit.repository

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.github.takezoe.resty.util.JsonUtils
import okhttp3._

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()
    Resty.register(new APIController(config))

    // TODO Use Scala HTTP client?
    println("Join to the cluster: " + config.controllerUrl) // TODO debug
    val client = new OkHttpClient()
    val request = new Request.Builder()
      .url(config.controllerUrl + "/api/nodes/join")
      .post(RequestBody.create(HttpClientSupport.ContentType_JSON, JsonUtils.serialize(Node("localhost", 8081)))) // TODO
      .build()
    val response = client.newCall(request).execute
    println(response.body.toString) // TOD debug
  }

}

case class Node(host: String, port: Int)
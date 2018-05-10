package com.github.takezoe.gitmesh.repository.util

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import Config._

case class Config(
  url: String,
  directory: String,
  controllerUrl: Seq[String],
  httpClient: HttpClientConfig
)

object Config {

  case class HttpClientConfig(
    requestTimeout: Long,
    idleTimeout: Long,
    maxConnections: Int,
    maxWaitQueue: Int,
    maxRetry: Int,
    retryInterval: Long
  )

  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url           = c.getString("gitmesh.url"),
      directory     = c.getString("gitmesh.directory"),
      controllerUrl = c.getStringList("gitmesh.controllerUrl").asScala,
      httpClient    = HttpClientConfig(
        requestTimeout = c.getLong("gitmesh.httpClient.requestTimeout"),
        idleTimeout    = c.getLong("gitmesh.httpClient.idleTimeout"),
        maxConnections = c.getInt("gitmesh.httpClient.maxConnections"),
        maxWaitQueue   = c.getInt("gitmesh.httpClient.maxWaitQueue"),
        maxRetry       = c.getInt("gitmesh.httpClient.maxRetry"),
        retryInterval  = c.getLong("gitmesh.httpClient.retryInterval")
      )
    )
  }
}
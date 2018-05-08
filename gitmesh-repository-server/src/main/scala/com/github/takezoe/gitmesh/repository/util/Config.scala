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
    maxAttempts: Int,
    retryInterval: Long
  )

  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url           = c.getString("gitmesh.url"),
      directory     = c.getString("gitmesh.directory"),
      controllerUrl = c.getStringList("gitmesh.controllerUrl").asScala,
      httpClient    = HttpClientConfig(
        maxAttempts   = c.getInt("gitmesh.httpClient.maxAttempts"),
        retryInterval = c.getLong("gitmesh.httpClient.retryInterval")
      )
    )
  }
}
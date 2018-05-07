package com.github.takezoe.gitmesh.repository.util

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

case class Config(
  url: String,
  directory: String,
  controllerUrl: Seq[String]
)

object Config {

  //val httpClientConfig = HttpClientConfig(maxRetry = 5, retryInterval = 500)

  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url           = c.getString("gitmesh.url"),
      directory     = c.getString("gitmesh.directory"),
      controllerUrl = c.getStringList("gitmesh.controllerUrl").asScala
    )
  }
}
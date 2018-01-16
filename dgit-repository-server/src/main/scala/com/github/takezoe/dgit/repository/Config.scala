package com.github.takezoe.dgit.repository

import com.github.takezoe.resty.HttpExecutorConfig
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

case class Config(
  url: String,
  directory: String,
  controllerUrl: Seq[String]
)

object Config {

  val httpExecutorConfig = HttpExecutorConfig(maxRetry = 5, retryInterval = 500) // TODO Be configurable

  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url           = c.getString("dgit.url"),
      directory     = c.getString("dgit.directory"),
      controllerUrl = c.getStringList("dgit.controllerUrl").asScala
    )
  }
}
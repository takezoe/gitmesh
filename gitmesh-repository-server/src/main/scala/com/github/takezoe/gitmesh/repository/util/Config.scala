package com.github.takezoe.gitmesh.repository.util

import com.github.takezoe.resty.HttpClientConfig
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

case class Config(
  url: String,
  directory: String,
  controllerUrl: Seq[String]//,
//  httpClient: HttpClientConfig
)

object Config {

  //val httpClientConfig = HttpClientConfig(maxRetry = 5, retryInterval = 500)

  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url           = c.getString("gitmesh.url"),
      directory     = c.getString("gitmesh.directory"),
      controllerUrl = c.getStringList("gitmesh.controllerUrl").asScala//,
//      httpClient    = HttpClientConfig(
//        maxRetry      = c.getInt("resty.httpClient.maxRetry"),
//        retryInterval = c.getInt("resty.httpClient.retryInterval"),
//        maxFailure    = c.getInt("resty.httpClient.maxFailure"),
//        resetInterval = c.getInt("resty.httpClient.resetInterval")
//      )
    )
  }
}
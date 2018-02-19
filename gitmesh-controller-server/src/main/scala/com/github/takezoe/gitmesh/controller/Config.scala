package com.github.takezoe.gitmesh.controller

import com.github.takezoe.resty.HttpClientConfig
import com.typesafe.config.ConfigFactory

case class Config(
  url: String,
  replica: Int,
  maxDiskUsage: Double,
  database: DatabaseConfig
)

case class DatabaseConfig(
  driver: String,
  url: String,
  user: String,
  password: String,
  connectionTimeout: Option[Long],
  idleTimeout: Option[Long],
  maxLifetime: Option[Long],
  minimumIdle: Option[Int],
  maximumPoolSize: Option[Int]
)

object Config {

  val httpClientConfig = HttpClientConfig(maxRetry = 5, retryInterval = 500)

  def load(): Config = {
    implicit val c = ConfigFactory.load()
    Config(
      url          = c.getString("gitmesh.url"),
      replica      = c.getInt("gitmesh.replica"),
      maxDiskUsage = c.getDouble("gitmesh.maxDiskUsage"),
      database     = DatabaseConfig(
        driver            = c.getString("gitmesh.database.driver"),
        url               = c.getString("dggitmeshit.database.url"),
        user              = c.getString("gitmesh.database.user"),
        password          = c.getString("gitmesh.database.password"),
        connectionTimeout = getOptionValue("gitmesh.database.connectionTimeout", c.getLong),
        idleTimeout       = getOptionValue("gitmesh.database.idleTimeout", c.getLong),
        maxLifetime       = getOptionValue("gitmesh.database.maxLifetime", c.getLong),
        minimumIdle       = getOptionValue("gitmesh.database.minimumIdle", c.getInt),
        maximumPoolSize   = getOptionValue("gitmesh.database.maximumPoolSize", c.getInt)
      )
    )
  }

  private def getOptionValue[T](path: String, f: String => T)
                               (implicit c: com.typesafe.config.Config): Option[T] = {
    if(c.hasPath(path)) Some(f(path)) else None
  }
}

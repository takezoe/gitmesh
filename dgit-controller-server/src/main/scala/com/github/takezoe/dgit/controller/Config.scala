package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.HttpExecutorConfig
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

  val httpExecutorConfig = HttpExecutorConfig(maxRetry = 5, retryInterval = 500) // TODO Be configurable

  def load(): Config = {
    implicit val c = ConfigFactory.load()
    Config(
      url          = c.getString("dgit.url"),
      replica      = c.getInt("dgit.replica"),
      maxDiskUsage = c.getDouble("dgit.maxDiskUsage"),
      database     = DatabaseConfig(
        driver            = c.getString("dgit.database.driver"),
        url               = c.getString("dgit.database.url"),
        user              = c.getString("dgit.database.user"),
        password          = c.getString("dgit.database.password"),
        connectionTimeout = getOptionValue("dgit.database.connectionTimeout", c.getLong),
        idleTimeout       = getOptionValue("dgit.database.idleTimeout", c.getLong),
        maxLifetime       = getOptionValue("dgit.database.maxLifetime", c.getLong),
        minimumIdle       = getOptionValue("dgit.database.minimumIdle", c.getInt),
        maximumPoolSize   = getOptionValue("dgit.database.maximumPoolSize", c.getInt)
      )
    )
  }

  private def getOptionValue[T](path: String, f: String => T)
                               (implicit c: com.typesafe.config.Config): Option[T] = {
    if(c.hasPath(path)) Some(f(path)) else None
  }
}

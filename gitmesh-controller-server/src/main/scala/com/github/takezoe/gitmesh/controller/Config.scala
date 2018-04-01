package com.github.takezoe.gitmesh.controller

import com.github.takezoe.resty.HttpClientConfig
import com.typesafe.config.ConfigFactory
import Config._

case class Config(
  url: String,
  replica: Int,
  maxDiskUsage: Double,
  database: DatabaseConfig,
  deadDetectionPeriod: DeadDetectionPeriod,
  repositoryLock: RepositoryLock,
  httpClient: HttpClientConfig
)

object Config {

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

  case class DeadDetectionPeriod(
    node: Long,
    master: Long
  )

  case class RepositoryLock(
    maxRetry: Int,
    retryInterval: Long
  )

  def load(): Config = {
    implicit val c = ConfigFactory.load()
    Config(
      url          = c.getString("gitmesh.url"),
      replica      = c.getInt("gitmesh.replica"),
      maxDiskUsage = c.getDouble("gitmesh.maxDiskUsage"),
      database     = Config.DatabaseConfig(
        driver            = c.getString("gitmesh.database.driver"),
        url               = c.getString("gitmesh.database.url"),
        user              = c.getString("gitmesh.database.user"),
        password          = c.getString("gitmesh.database.password"),
        connectionTimeout = getOptionValue("gitmesh.database.connectionTimeout", c.getLong),
        idleTimeout       = getOptionValue("gitmesh.database.idleTimeout", c.getLong),
        maxLifetime       = getOptionValue("gitmesh.database.maxLifetime", c.getLong),
        minimumIdle       = getOptionValue("gitmesh.database.minimumIdle", c.getInt),
        maximumPoolSize   = getOptionValue("gitmesh.database.maximumPoolSize", c.getInt)
      ),
      deadDetectionPeriod = Config.DeadDetectionPeriod(
        node   = c.getLong("gitmesh.deadDetectionPeriod.node"),
        master = c.getLong("gitmesh.deadDetectionPeriod.master")
      ),
      repositoryLock = Config.RepositoryLock(
        maxRetry      = c.getInt("gitmesh.repositoryLock.maxRetry"),
        retryInterval = c.getLong("gitmesh.repositoryLock.retryInterval")
      ),
      httpClient = HttpClientConfig(
        maxRetry      = c.getInt("resty.httpClient.maxRetry"),
        retryInterval = c.getInt("resty.httpClient.retryInterval"),
        maxFailure    = c.getInt("resty.httpClient.maxFailure"),
        resetInterval = c.getInt("resty.httpClient.resetInterval")
      )
    )
  }

  private def getOptionValue[T](path: String, f: String => T)
                               (implicit c: com.typesafe.config.Config): Option[T] = {
    if(c.hasPath(path)) Some(f(path)) else None
  }
}

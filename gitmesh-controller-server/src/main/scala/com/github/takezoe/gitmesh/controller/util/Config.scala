package com.github.takezoe.gitmesh.controller.util

import com.github.takezoe.gitmesh.controller.util.Config._
import com.typesafe.config.ConfigFactory

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

  case class HttpClientConfig(
    requestTimeout: Long,
    idleTimeout: Long,
    maxConnections: Int,
    maxWaitQueue: Int,
    maxRetry: Int,
    retryInterval: Long
  )

  def load(): Config = {
    implicit val c = ConfigFactory.load()
    Config(
      url          = getString("gitmesh.url", c),
      replica      = getInt("gitmesh.replica", c),
      maxDiskUsage = getDouble("gitmesh.maxDiskUsage", c),
      database     = Config.DatabaseConfig(
        driver            = getString("gitmesh.database.driver", c),
        url               = getString("gitmesh.database.url", c),
        user              = getString("gitmesh.database.user", c),
        password          = getString("gitmesh.database.password", c),
        connectionTimeout = getOptionLong("gitmesh.database.connectionTimeout", c),
        idleTimeout       = getOptionLong("gitmesh.database.idleTimeout", c),
        maxLifetime       = getOptionLong("gitmesh.database.maxLifetime", c),
        minimumIdle       = getOptionInt("gitmesh.database.minimumIdle", c),
        maximumPoolSize   = getOptionInt("gitmesh.database.maximumPoolSize", c)
      ),
      deadDetectionPeriod = Config.DeadDetectionPeriod(
        node   = getLong("gitmesh.deadDetectionPeriod.node", c),
        master = getLong("gitmesh.deadDetectionPeriod.master", c)
      ),
      repositoryLock = Config.RepositoryLock(
        maxRetry      = getInt("gitmesh.repositoryLock.maxRetry", c),
        retryInterval = getLong("gitmesh.repositoryLock.retryInterval", c)
      ),
      httpClient = HttpClientConfig(
        requestTimeout = getLong("gitmesh.httpClient.requestTimeout", c),
        idleTimeout    = getLong("gitmesh.httpClient.idleTimeout", c),
        maxConnections = getInt("gitmesh.httpClient.maxConnections", c),
        maxWaitQueue   = getInt("gitmesh.httpClient.maxWaitQueue", c),
        maxRetry       = getInt("gitmesh.httpClient.maxRetry", c),
        retryInterval  = getInt("gitmesh.httpClient.retryInterval", c)
      )
    )
  }

  private def getString(key: String, config: com.typesafe.config.Config): String = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      property
    } else {
      config.getString(key)
    }
  }

  private def getOptionString(key: String, config: com.typesafe.config.Config): Option[String] = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      Some(property)
    } else {
      if(config.hasPath(key)) Some(config.getString(key)) else None
    }
  }


  private def getInt(key: String, config: com.typesafe.config.Config): Int = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      property.toInt
    } else {
      config.getInt(key)
    }
  }

  private def getOptionInt(key: String, config: com.typesafe.config.Config): Option[Int] = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      Some(property.toInt)
    } else {
      if(config.hasPath(key)) Some(config.getInt(key)) else None
    }
  }

  private def getLong(key: String, config: com.typesafe.config.Config): Long = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      property.toLong
    } else {
      config.getLong(key)
    }
  }

  private def getOptionLong(key: String, config: com.typesafe.config.Config): Option[Long] = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      Some(property.toLong)
    } else {
      if(config.hasPath(key)) Some(config.getLong(key)) else None
    }
  }

  private def getDouble(key: String, config: com.typesafe.config.Config): Double = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      property.toDouble
    } else {
      config.getDouble(key)
    }
  }

  private def getOptionDouble(key: String, config: com.typesafe.config.Config): Option[Double] = {
    val property = getEnvOrSystemProperty(key)
    if(property != null && property.nonEmpty){
      Some(property.toDouble)
    } else {
      if(config.hasPath(key)) Some(config.getDouble(key)) else None
    }
  }

  private def getEnvOrSystemProperty(key: String): String = {
    val env = System.getenv(key)
    if(env != null && env.nonEmpty){
      env
    } else {
      System.getProperty(key)
    }
  }

}

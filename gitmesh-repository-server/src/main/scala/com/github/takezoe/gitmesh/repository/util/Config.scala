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
      url           = getString("gitmesh.url", c),
      directory     = getString("gitmesh.directory", c),
      controllerUrl = getString("gitmesh.controllerUrl", c).split(",").map(_.trim),
      httpClient    = HttpClientConfig(
        requestTimeout = getLong("gitmesh.httpClient.requestTimeout", c),
        idleTimeout    = getLong("gitmesh.httpClient.idleTimeout", c),
        maxConnections = getInt("gitmesh.httpClient.maxConnections", c),
        maxWaitQueue   = getInt("gitmesh.httpClient.maxWaitQueue", c),
        maxRetry       = getInt("gitmesh.httpClient.maxRetry", c),
        retryInterval  = getLong("gitmesh.httpClient.retryInterval", c)
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
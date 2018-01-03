package com.github.takezoe.dgit.controller

import com.typesafe.config.ConfigFactory

case class Config(replica: Int, maxDiskUsage: Double)

object Config {
  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      replica      = c.getInt("dgit.replica"),
      maxDiskUsage = c.getDouble("dgit.maxDiskUsage")
    )
  }
}

package com.github.takezoe.dgit.repository

import com.typesafe.config.ConfigFactory

case class Config(
  endpoint: String,
  directory: String,
  controllerUrl: String
)

object Config {
  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      endpoint = c.getString("dgit.endpoint"),
      directory = c.getString("dgit.directory"),
      controllerUrl = c.getString("dgit.controllerUrl")
    )
  }
}
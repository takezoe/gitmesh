package com.github.takezoe.dgit.repository

import com.typesafe.config.ConfigFactory

case class Config(
  url: String,
  directory: String,
  controllerUrl: String
)

object Config {
  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      url = c.getString("dgit.url"),
      directory = c.getString("dgit.directory"),
      controllerUrl = c.getString("dgit.controllerUrl")
    )
  }
}
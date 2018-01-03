package com.github.takezoe.dgit.repository

import com.typesafe.config.ConfigFactory

case class Config(
  directory: String,
  controllerUrl: String
)

object Config {
  def load(): Config = {
    val c = ConfigFactory.load()
    Config(
      directory = c.getString("dgit.directory"),
      controllerUrl = c.getString("dgit.controllerUrl")
    )
  }
}
package com.github.takezoe.dgit.repository

case class Config(
  dir: String,
  controllerUrl: String
)

object Config {
  // TODO load from the configuration file and keep instance as singleton
  def load(): Config = {
    Config("/tmp/dgit/node1", "http://localhost:8080")
  }
}
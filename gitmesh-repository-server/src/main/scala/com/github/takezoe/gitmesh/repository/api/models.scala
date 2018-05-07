package com.github.takezoe.gitmesh.repository.api

object models {

  case class CloneRequest(nodeUrl: String, empty: Boolean)
  case class Repository(name: String, empty: Boolean)
  case class Status(url: String, diskUsage: Double, repos: Seq[String])
  case class SynchronizedRequest(nodeUrl: String)
  case class SynchronizeRequest(nodeUrl: String)

}

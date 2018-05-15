package com.github.takezoe.gitmesh.controller.api

object models {

  case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
  case class JoinNodeRepository(name: String, timestamp: Long)

  case class SynchronizedRequest(nodeUrl: String)

  case class RepositoryInfo(name: String, primaryNode: Option[String], timestamp: Long, nodes: Seq[RepositoryNodeInfo])
  case class RepositoryNodeInfo(url: String, status: String)

  case class NodeStatus(url: String, timestamp: Long, diskUsage: Double, repos: Seq[NodeStatusRepository])
  case class NodeStatusRepository(name: String, status: String)

  case class CloneRequest(nodeUrl: String, empty: Boolean)

  case class ErrorModel(errors: Seq[String])

}

package com.github.takezoe.gitmesh.controller.data

object models {

  // Initial value of LAST_UPDATE_TIME column of REPOSITORY table
  val InitialRepositoryId = -1L

  case class Node(nodeUrl: String, lastUpdateTime: Long, diskUsage: Double)
  case class Repository(repositoryName: String, primaryNode: Option[String], lastUpdateTime: Long)
  case class NodeRepository(nodeUrl: String, repositoryName: String, status: String)

  object NodeRepositoryStatus {
    val Preparing = "PREPARING"
    val Ready = "READY"
  }

  case class ExclusiveLock(lockKey: String, comment: Option[String], lockTime: Long)

}

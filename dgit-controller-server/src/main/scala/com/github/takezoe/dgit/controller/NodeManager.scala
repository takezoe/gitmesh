package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

import com.github.takezoe.resty.HttpClientSupport

import scala.collection.JavaConverters._
import models.CloneRequest
import org.slf4j.LoggerFactory

// TODO Should be a class?
object NodeManager extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)
  private val nodes = new ConcurrentHashMap[String, NodeStatus]()
  private val primaryNodeOfRepository = new ConcurrentHashMap[String, String]()

  def updateNodeStatus(node: String, diskUsage: Double, repos: Seq[String]): Unit = {
    if(!nodes.containsKey(node)){
      log.info(s"Added a repository node: $node")
    }
    nodes.put(node, NodeStatus(System.currentTimeMillis(), diskUsage, repos))

    // Set a primary node of repositories
    repos.foreach { repository =>
      Option(primaryNodeOfRepository.get(repository)) match {
        case None =>
          primaryNodeOfRepository.put(repository, node)

        case Some(primaryEndpoint) =>
          httpPutJson[String](
            s"$node/api/repos/$repository",
            CloneRequest(s"$primaryEndpoint/git/$repository.git")
          ) match {
            case Right(_) =>
            case Left(e) => log.error(e.errors.mkString("\n")) // TODO What to do in this case?
          }
      }
    }
  }

  def removeNode(node: String): Unit = {
    nodes.remove(node)

    primaryNodeOfRepository.forEach { case (repository, primaryNode) =>
      if(node == primaryNode){
        nodes.asScala.find { case (_, status) => status.repos.contains(repository) } match {
          case Some((newNode, _)) => {
            // Update the primary node
            primaryNodeOfRepository.put(repository, newNode)
          }
          case None => {
            log.error(s"All nodes for $repository has been retired.")
            primaryNodeOfRepository.remove(repository)
          }
        }
      }
    }
  }

  def allNodes(): Seq[(String, NodeStatus)] = {
    nodes.asScala.toSeq
  }

//  def getTimestamp(node: String): Option[Long] = {
//    Option(nodes.get(node)).map(_.timestamp)
//  }

  def selectNode(repository: String): Option[String] = {
    Option(primaryNodeOfRepository.get(repository))
  }

  def selectNodes(repository: String): Seq[String] = {
    nodes.asScala.collect { case (node, status) if status.repos.contains(repository) => node }.toSeq
  }

  def selectAvailableNode(): Option[String] = ???

  def allRepositories(): Seq[Repository] = {
    nodes.asScala.toSeq
      .flatMap { case (node, status) => status.repos.map { name => (name, node) } }
      .groupBy { case (name, _) => name }
      .map { case (name, nodes) =>
        Repository(
          name,
          selectNode(name).get, // TODO Don't use Option.get!
          nodes.map { case (_, node) => node }
        )
      }
      .toSeq
  }

}

case class Repository(name: String, primaryNode: String, nodes: Seq[String])
case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[String])
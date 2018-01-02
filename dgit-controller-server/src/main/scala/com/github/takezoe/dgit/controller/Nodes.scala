package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

import com.github.takezoe.resty.HttpClientSupport

import scala.collection.JavaConverters._
import models.CloneRequest

// TODO Should be a class?
object Nodes extends HttpClientSupport {

  private val nodes = new ConcurrentHashMap[String, NodeStatus]()
  private val primaryNodeOfRepository = new ConcurrentHashMap[String, String]()

  def updateNodeStatus(endpoint: String, diskUsage: Double, repos: Seq[String]): Unit = {
    if(!nodes.containsKey(endpoint)){
      println(s"[INFO] Added a repository node: $endpoint") // TODO debug
    }
    nodes.put(endpoint, NodeStatus(System.currentTimeMillis(), diskUsage, repos))

    // Set a primary node of repositories
    repos.foreach { repository =>
      Option(primaryNodeOfRepository.get(repository)) match {
        case None =>
          primaryNodeOfRepository.put(repository, endpoint)

        case Some(primaryEndpoint) =>
          httpPutJson[String](
            s"$endpoint/api/repos/$repository",
            CloneRequest(s"$primaryEndpoint/git/$repository.git")
          ) match {
            case Right(_) =>
            case Left(e) => println(e.errors) // TODO What to do in this case?
          }
      }
    }
  }

  def removeNode(endpoint: String): Unit = {
    nodes.remove(endpoint)

    primaryNodeOfRepository.forEach { case (repository, primaryEndpoint) =>
      if(endpoint == primaryEndpoint){
        nodes.asScala.find { case (_, status) => status.repos.contains(repository) } match {
          case Some((newEndpoint, _)) => {
            // Update the primary node
            primaryNodeOfRepository.put(repository, newEndpoint)
          }
          case None => {
            println(s"[ERROR] All nodes for $repository has been retired.") // TODO debug
            primaryNodeOfRepository.remove(repository)
          }
        }
      }
    }
  }

  def allNodes(): Seq[(String, NodeStatus)] = {
    nodes.asScala.toSeq
  }

  def getTimestamp(endpoint: String): Option[Long] = {
    Option(nodes.get(endpoint)).map(_.timestamp)
  }

  def selectNode(repository: String): Option[String] = {
    Option(primaryNodeOfRepository.get(repository))
  }

  def selectNodes(repository: String): Seq[String] = {
    nodes.asScala.collect { case (endpoint, status) if status.repos.contains(repository) => endpoint }.toSeq
  }

}

//case class Node(endpoint: String)
case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[String])
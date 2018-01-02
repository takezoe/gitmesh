package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

object Nodes {

  val nodes = new ConcurrentHashMap[String, NodeStatus]()

  def updateNodeInfo(endpoint: String, diskUsage: Double): Unit = {
    if(!nodes.containsKey(endpoint)){
      println("Added a repository node: " + endpoint) // TODO debug
    }
    nodes.put(endpoint, NodeStatus(System.currentTimeMillis(), diskUsage))
  }

  def removeNode(emdpoint: String): Unit = {
    nodes.remove(emdpoint)
  }

  def allNodes(): Seq[String] = {
    nodes.asScala.keys.toSeq
  }

  def getTimestamp(endpoint: String): Option[Long] = {
    Option(nodes.get(endpoint)).map(_.timestamp)
  }

  def selectNode(repository: String): Option[String] = {
    // TODO Not implemented yet
    Some(nodes.entrySet().asScala.head.getKey)
  }

  def selectNodes(repository: String): Seq[String] = {
    // TODO Not implemented yet
    Nil
  }

}

//case class Node(endpoint: String)
case class NodeStatus(timestamp: Long, diskUsage: Double)
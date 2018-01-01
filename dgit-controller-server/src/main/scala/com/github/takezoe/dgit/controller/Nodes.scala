package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

object Nodes {

  val nodes = new ConcurrentHashMap[Node, Long]()

  def add(node: Node): Unit = {
    if(!nodes.containsKey(node)){
      println("Added a repository node: " + node.host + ":" + node.port) // TODO debug
    }
    nodes.put(node, System.currentTimeMillis())
  }

  def remove(node: Node): Unit = {
    nodes.remove(node)
  }

  def all(): Seq[Node] = {
    nodes.asScala.keys.toSeq
  }

  def timestamp(node: Node): Option[Long] = {
    Option(nodes.get(node))
  }

  def selectNode(repository: String): Option[Node] = {
    // TODO Not implemented yet
    Some(nodes.entrySet().asScala.head.getKey)
  }

  def selectNodes(repository: String): Seq[Node] = {
    // TODO Not implemented yet
    Nil
  }

}

case class Node(host: String, port: Int)
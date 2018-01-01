package com.github.takezoe.dgit.controller

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import scala.collection.JavaConverters._

class Nodes {

  val nodes = new ConcurrentHashMap[Node, Long]()

  def add(node: Node): Unit = {
    println("Added a repository node: " + node.host + ":" + node.port) // TODO debug
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

}

case class Node(host: String, port: Int)
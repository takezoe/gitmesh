package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._

class Nodes {

  var nodes = new ConcurrentLinkedQueue[Node]()

  def add(node: Node): Unit = {
    println("Added a repository node: " + node.host + ":" + node.port) // TODO debug
    nodes.add(node)
  }

  def remove(node: Node): Unit = {
    nodes.remove(node)
  }

  def all(): Seq[Node] = {
    nodes.asScala.toSeq
  }

}

case class Node(host: String, port: Int)
package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.{Action, HttpClientSupport}
import models.Node

class APIController(config: Config) extends HttpClientSupport {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: Node): Unit = {
    Nodes.updateNodeStatus(node.node, node.diskUsage, node.repos)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = {
    Nodes.allNodes().map { case (node, status) =>
      models.Node(node, status.diskUsage, status.repos)
    }
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    val nodes = Nodes.allNodes()
      .filter { case (_, status) => status.diskUsage < config.maxDiskUsage }
      .take(config.replica)

    if(nodes.nonEmpty){
      nodes.foreach { case (node, status) =>
        httpPost(s"$node/api/repos/${name}", Map.empty)
        // update repository status immediately
        Nodes.updateNodeStatus(node, status.diskUsage, status.repos :+ name)
      }
    } else {
      throw new RuntimeException("There are no nodes which can accommodate a new repository")
    }
  }

}

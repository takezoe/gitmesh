package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.{Action, HttpClientSupport}

class APIController(config: Config) extends HttpClientSupport {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: Node): Unit = {
    NodeManager.updateNodeStatus(node.node, node.diskUsage, node.repos)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = {
    NodeManager.allNodes().map { case (node, status) =>
      Node(node, status.diskUsage, status.repos)
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[Repository] = {
    NodeManager.allRepositories()
  }

  @Action(method="DELETE", path = "/api/repos/{name}")
  def deleteRepository(name: String): Unit = {
    NodeManager.selectNodes(name).foreach { deleteNode =>
      httpDelete[String](s"$deleteNode/api/repos/$name")

      NodeManager.allNodes()
        .find { case (node, _) => node == deleteNode }
        .foreach { case (node, status) =>
          NodeManager.updateNodeStatus(node, status.diskUsage, status.repos.filterNot(_ == name))
        }
    }
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    val nodes = NodeManager.allNodes()
      .filter { case (_, status) => status.diskUsage < config.maxDiskUsage }
      .take(config.replica)

    if(nodes.nonEmpty){
      nodes.foreach { case (node, status) =>
        httpPost(s"$node/api/repos/${name}", Map.empty)
        // update repository status immediately
        NodeManager.updateNodeStatus(node, status.diskUsage, status.repos :+ name)
      }
    } else {
      throw new RuntimeException("There are no nodes which can accommodate a new repository")
    }
  }

}

case class CloneRequest(source: String)
case class Node(node: String, diskUsage: Double, repos: Seq[String])

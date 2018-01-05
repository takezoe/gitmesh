package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.{Action, HttpClientSupport}

class APIController(config: Config) extends HttpClientSupport {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: Node): Unit = {
    NodeManager.updateNodeStatus(node.url, node.diskUsage, node.repos)
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

  @Action(method = "DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String): Unit = {
    NodeManager.getNodeUrlsOfRepository(repositoryName).foreach { nodeUrl =>
      httpDelete[String](s"$nodeUrl/api/repos/$repositoryName")

      NodeManager.getNodeStatus(nodeUrl).foreach { status =>
        NodeManager.updateNodeStatus(nodeUrl, status.diskUsage, status.repos.filterNot(_ == repositoryName))
      }
    }
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}")
  def createRepository(repositoryName: String): Unit = {
    val nodeUrls = NodeManager.allNodes()
      .filter { case (_, status) => status.diskUsage < config.maxDiskUsage }
      .take(config.replica)

    if(nodeUrls.nonEmpty){
      nodeUrls.foreach { case (nodeUrl, status) =>
        httpPost(s"$nodeUrl/api/repos/${repositoryName}", Map.empty)
        // update repository status immediately
        NodeManager.updateNodeStatus(nodeUrl, status.diskUsage, status.repos :+ repositoryName)
      }
    } else {
      throw new RuntimeException("There are no nodes which can accommodate a new repository")
    }
  }

}

case class Node(url: String, diskUsage: Double, repos: Seq[String])

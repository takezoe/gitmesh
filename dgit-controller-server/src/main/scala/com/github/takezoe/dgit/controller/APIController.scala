package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.{Action, HttpClientSupport}

class APIController(config: Config) extends HttpClientSupport {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: models.Node): Unit = {
    Nodes.updateNodeStatus(node.endpoint, node.diskUsage, node.repos)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[models.Node] = {
    Nodes.allNodes().map { case (endpoint, status) =>
      models.Node(endpoint, status.diskUsage, status.repos)
    }
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    val nodes = Nodes.allNodes()
      .collect { case (endpoint, status) if status.diskUsage < config.maxDiskUsage => endpoint }
      .take(config.replica)

    if(nodes.nonEmpty){
      nodes.foreach { endpoint =>
        httpPost(s"$endpoint/api/repos/${name}", Map.empty)
      }
    } else {
      throw new RuntimeException("There are no nodes which can accommodate a new repository")
    }
  }

}

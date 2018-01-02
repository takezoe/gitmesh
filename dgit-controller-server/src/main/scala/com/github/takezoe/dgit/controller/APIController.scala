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
      .filter { case (endpoint, status) => status.diskUsage < config.maxDiskUsage }
      .take(config.replica)

    if(nodes.nonEmpty){
      nodes.foreach { case (endpoint, status) =>
        httpPost(s"$endpoint/api/repos/${name}", Map.empty)
        // update repository status immediately
        Nodes.updateNodeStatus(endpoint, status.diskUsage, status.repos :+ name)
      }
    } else {
      throw new RuntimeException("There are no nodes which can accommodate a new repository")
    }
  }

}

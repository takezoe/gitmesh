package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.Action

class APIController(config: Config) {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: models.Node): Unit = {
    Nodes.updateNodeStatus(node.endpoint, node.diskUsage, node.repos)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[String] = {
    Nodes.allNodes()
  }

}

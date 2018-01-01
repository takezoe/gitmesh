package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.Action

class APIController(nodes: Nodes) {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: Node): Unit = {
    nodes.add(node)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = {
    nodes.all()
  }

}

package com.github.takezoe.dgit.controller

import com.github.takezoe.resty.Action

class APIController {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: Node): Unit = {
    Nodes.add(node)
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = {
    Nodes.all()
  }

}

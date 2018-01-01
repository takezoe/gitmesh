package com.github.takezoe.dgit.controller

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class GitRepositoryProxyServer extends HttpServlet {

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    Nodes.selectNode("xxxx").map { node =>
      // TODO Proxy request
    }.getOrElse {
      // TODO NotFound
    }
  }

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    Nodes.selectNode("xxxx").map { node =>
      // TODO Proxy request
    }.getOrElse {
      // TODO NotFound
    }
  }

}

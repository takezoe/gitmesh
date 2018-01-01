package com.github.takezoe.dgit.controller

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class GitRepositoryProxyServer extends HttpServlet {

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = super.doPost(req, resp)

  override def doPut(req: HttpServletRequest, resp: HttpServletResponse) = super.doPut(req, resp)

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = super.doGet(req, resp)

}

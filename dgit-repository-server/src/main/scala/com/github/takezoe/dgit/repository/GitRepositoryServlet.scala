package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.ServletConfig
import javax.servlet.http.HttpServletRequest

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.transport.resolver.FileResolver

import syntax._

class GitRepositoryServlet extends GitServlet {

  override def init(config: ServletConfig): Unit = {
    defining(Config.load()){ config =>
      val root: File = new File(config.directory)
      setRepositoryResolver(new DGitFileResolver[HttpServletRequest](root, true))
    }
    super.init(config)
  }

//  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
//    println(req.getMethod + " " + req.getRequestURI + (if(req.getQueryString == null) "" else "?" + req.getQueryString))
//    super.service(req, res)
//  }

}

class DGitFileResolver[T](basePath: File, exposeAll: Boolean) extends FileResolver[T](basePath, exposeAll) {
  override def open(req: T, name: String) = {
    super.open(req, name.replaceFirst("\\.git$", ""))
  }
}

package com.github.takezoe.dgit.repository

import java.io.File
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.transport.resolver.FileResolver

class GitRepositoryServlet extends GitServlet {

  override def init(config: ServletConfig): Unit = {
    val dataNodeConfig = Config.load()

    val root: File = new File(dataNodeConfig.dir)
    setRepositoryResolver(new FileResolver2[HttpServletRequest](root, true))

    super.init(config)
  }

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    println(req.getRequestURI)
    super.service(req, res)
  }

}

class FileResolver2[T](basePath: File, exposeAll: Boolean) extends FileResolver[T](basePath, exposeAll) {
  override def open(req: T, name: String) = {
    super.open(req, name.replaceFirst("\\.git$", ""))
  }
}

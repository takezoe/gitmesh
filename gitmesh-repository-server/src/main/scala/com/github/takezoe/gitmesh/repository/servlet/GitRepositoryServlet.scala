package com.github.takezoe.gitmesh.repository.servlet

import java.io.File
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.github.takezoe.gitmesh.repository.util._
import com.github.takezoe.gitmesh.repository.util.syntax._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.http.server.GitServlet
import org.eclipse.jgit.transport.resolver.FileResolver

class GitRepositoryServlet extends GitServlet {

  private val config = Config.load()

  override def init(config: ServletConfig): Unit = {
    defining(Config.load()){ config =>
      val root: File = new File(config.directory)
      setRepositoryResolver(new GitMeshFileResolver[HttpServletRequest](root, true))
    }
    super.init(config)
  }

  private val RepositoryNamePattern = ".+/(.+?)\\.git.*".r

  override def service(req: HttpServletRequest, res: HttpServletResponse): Unit = {
    val timestamp = req.getHeader("GITMESH-UPDATE-ID")

    if(timestamp != null){
      req.getRequestURI match {
        case RepositoryNamePattern(repositoryName) =>
          val file = new File(config.directory, s"$repositoryName.id")
          FileUtils.write(file, timestamp.toString, "UTF-8")
        case _ =>
      }
    }

    super.service(req, res)
  }

}

class GitMeshFileResolver[T](basePath: File, exposeAll: Boolean) extends FileResolver[T](basePath, exposeAll) {
  override def open(req: T, name: String) = {
    super.open(req, name.replaceFirst("\\.git$", ""))
  }
}

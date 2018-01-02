package com.github.takezoe.dgit.repository

import java.io.File

import com.github.takezoe.resty._
import org.eclipse.jgit.lib.RepositoryBuilder
import Utils._
import com.github.takezoe.dgit.repository.models.{Result, CloneRequest}

class APIController(config: Config) {

  @Action(method="GET", path = "/")
  def status(): Result = {
    Result("OK")
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    println("Creating repository: " + name)
    using(new RepositoryBuilder().setGitDir(new File(config.dir, name)).setBare.build){ repository =>
      repository.create(true)
      val config = repository.getConfig
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[String] = {
    val file = new File(config.dir)
    file.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  @Action(method = "PUT", path = "/api/repos/{name}")
  def cloneRepository(name: String, request: CloneRequest) = {
    // TODO Not implemented yet
    println(request.source)
  }

}

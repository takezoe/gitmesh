package com.github.takezoe.dgit.repository

import java.io.File

import com.github.takezoe.resty._
import org.eclipse.jgit.lib.RepositoryBuilder
import Utils._
import com.github.takezoe.dgit.repository.models.{CloneRequest, Result}
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git

class APIController(config: Config) {

  @Action(method="GET", path = "/")
  def status(): Result = {
    Result("OK")
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    println(s"Creating repository: $name")
    using(new RepositoryBuilder().setGitDir(new File(config.dir, name)).setBare.build){ repository =>
      repository.create(true)
      val config = repository.getConfig
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[String] = {
    val rootDir = new File(config.dir)
    rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  @Action(method = "PUT", path = "/api/repos/{name}")
  def cloneRepository(name: String, request: CloneRequest): Unit = {
    println(s"sync $name with ${request.source}")
    val rootDir = new File(config.dir)
    val repositoryDir = new File(rootDir, name)

    // Delete the repository directory if it exists
    if(repositoryDir.exists){
      FileUtils.forceDelete(repositoryDir)
    }

    // Clone from the source repository
    Git.cloneRepository().setBare(true)
      .setURI(request.source)
      .setDirectory(repositoryDir)
      .setCloneAllBranches(true)
      .call()
  }

}

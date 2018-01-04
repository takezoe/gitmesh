package com.github.takezoe.dgit.repository

import java.io.File

import com.github.takezoe.resty._
import org.eclipse.jgit.lib.RepositoryBuilder
import Utils._
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory

class APIController(config: Config) {

  private val log = LoggerFactory.getLogger(classOf[APIController])

  @Action(method="GET", path = "/")
  def status(): Status = {
    val rootDir = new File(config.directory)
    val diskUsage = rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble
    val repos = rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)

    Status(
      endpoint = config.endpoint,
      diskUsage = diskUsage,
      repos = repos
    )
  }

  @Action(method="POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    log.info(s"Create repository: $name")

    using(new RepositoryBuilder().setGitDir(new File(config.directory, name)).setBare.build){ repository =>
      repository.create(true)
      val config = repository.getConfig
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[String] = {
    val rootDir = new File(config.directory)
    rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  @Action(method="DELETE", path = "/api/repos/{name}")
  def deleteRepository(name: String): Unit = {
    log.info(s"Delete repository: $name")

    val rootDir = new File(config.directory)
    val repositoryDir = new File(rootDir, name)

    if(repositoryDir.exists){
      FileUtils.forceDelete(repositoryDir)
    }
  }

  @Action(method = "PUT", path = "/api/repos/{name}")
  def cloneRepository(name: String, request: CloneRequest): Unit = {
    //val cloneUrl = s"${config.controllerUrl}/git/$name.git"
    log.info(s"Synchronize repository: $name with ${request.source}")

    val rootDir = new File(config.directory)
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

case class Status(endpoint: String, diskUsage: Double, repos: Seq[String])
case class CloneRequest(source: String)
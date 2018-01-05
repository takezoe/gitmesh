package com.github.takezoe.dgit.repository

import java.io.File

import syntax._

import com.github.takezoe.resty._
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

class APIController(implicit val config: Config) extends HttpClientSupport with GitOperations {

  private val log = LoggerFactory.getLogger(classOf[APIController])

  @Action(method = "GET", path = "/")
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

  @Action(method = "POST", path = "/api/repos/{name}")
  def createRepository(name: String): Unit = {
    log.info(s"Create repository: $name")
    gitInit(name)
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[String] = {
    val rootDir = new File(config.directory)
    rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  @Action(method="DELETE", path = "/api/repos/{name}")
  def deleteRepository(name: String): Unit = {
    log.info(s"Delete repository: $name")

    val dir = new File(config.directory, name)
    if(dir.exists){
      FileUtils.forceDelete(dir)
    }
  }

  @Action(method = "PUT", path = "/api/repos/{name}")
  def cloneRepository(name: String, request: CloneRequest): Unit = {
    val cloneUrl = s"${config.endpoint}/git/$name.git"
    log.info(s"Synchronize repository: $name with ${cloneUrl}")

    val dir = new File(config.directory, name).unsafeTap { dir =>
      // Delete the repository directory if it exists
      if(dir.exists){
        FileUtils.forceDelete(dir)
      }
    }

    httpGet[Repository](s"${request.endpoint}/api/repos/$name") match {
      case Left(e) => throw new RuntimeException(e.errors.mkString("\n"))
      // Source is an empty repository
      case Right(x) if x.empty => gitInit(name)
      // Clone from the source repository
      case _ => gitClone(name, cloneUrl)
    }
  }

}

case class Status(endpoint: String, diskUsage: Double, repos: Seq[String])
case class CloneRequest(endpoint: String)
case class Repository(name: String, empty: Boolean)
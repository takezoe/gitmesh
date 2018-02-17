package com.github.takezoe.gitmesh.repository

import java.io.File

import com.github.takezoe.resty._
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

class APIController(implicit val config: Config) extends HttpClientSupport with GitOperations {

  private val log = LoggerFactory.getLogger(classOf[APIController])
  implicit override val httpClientConfig = Config.httpClientConfig

  private def getRepositories(): Seq[String] = {
    val rootDir = new File(config.directory)
    rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  @Action(method = "GET", path = "/")
  def status(): Status = {
    val rootDir = new File(config.directory)
    val diskUsage = rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble
    val repos = getRepositories()

    Status(
      url = config.url,
      diskUsage = diskUsage,
      repos = repos
    )
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}")
  def createRepository(repositoryName: String,
                       @Param(from = "header", name = "DGIT-UPDATE-ID") timestamp: Long): ActionResult[Unit] = {
    // Delete the repository directory if it exists
    val dir = new File(config.directory, repositoryName)
    if(dir.exists){
      FileUtils.forceDelete(dir)
    }

    log.info(s"Create repository: $repositoryName")

    // git init
    gitInit(repositoryName)

    // Write timestamp
    val file = new File(config.directory, s"$repositoryName.id")
    FileUtils.write(file, timestamp.toString, "UTF-8")

    Ok((): Unit)
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[Repository] = {
    val rootDir = new File(config.directory)
    rootDir.listFiles(_.isDirectory).toSeq.map { dir =>
      Repository(dir.getName, gitCheckEmpty(dir.getName))
    }
  }

  @Action(method = "GET", path = "/api/repos/{repositoryName}")
  def showRepositoryStatus(repositoryName: String): ActionResult[Repository] = {
    val dir = new File(config.directory, repositoryName)
    if(dir.exists()){
      Ok(Repository(repositoryName, gitCheckEmpty(repositoryName)))
    } else {
      NotFound()
    }
  }

  @Action(method="DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String): Unit = {
    log.info(s"Delete repository: $repositoryName")

    val dir = new File(config.directory, repositoryName)
    if(dir.exists){
      FileUtils.forceDelete(dir)
    }

    val file = new File(config.directory, s"$repositoryName.id")
    if(file.exists){
      FileUtils.forceDelete(file)
    }
  }

  @Action(method = "PUT", path = "/api/repos/{repositoryName}")
  def cloneRepository(repositoryName: String, request: CloneRequest,
                      @Param(from = "header", name = "DGIT-UPDATE-ID") timestamp: Long): Unit = {
    val cloneUrl = s"${config.url}/git/$repositoryName.git"
    log.info(s"Synchronize repository: $repositoryName with ${cloneUrl}")

    // Delete the repository directory if it exists
    val dir = new File(config.directory, repositoryName)
    if(dir.exists){
      FileUtils.forceDelete(dir)
    }

    // Clone
    httpGet[Repository](s"${request.nodeUrl}/api/repos/$repositoryName") match {
      case Left(e) => throw new RuntimeException(e.errors.mkString("\n"))
      // Source is an empty repository
      case Right(x) if x.empty => gitInit(repositoryName)
      // Clone from the source repository
      case _ => gitClone(repositoryName, cloneUrl)
    }

    // write timestamp
    val file = new File(config.directory, s"$repositoryName.id")
    FileUtils.write(file, timestamp.toString, "UTF-8")
  }

}

case class Status(url: String, diskUsage: Double, repos: Seq[String])
case class CloneRequest(nodeUrl: String)
case class Repository(name: String, empty: Boolean)
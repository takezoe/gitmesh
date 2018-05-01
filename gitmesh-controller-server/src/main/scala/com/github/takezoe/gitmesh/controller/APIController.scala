package com.github.takezoe.gitmesh.controller

import javax.servlet.http.HttpServletResponse

import com.github.takezoe.resty._
import org.slf4j.LoggerFactory
import APIController._

class APIController(dataStore: DataStore)(implicit val config: Config) extends HttpClientSupport {

  implicit override val httpClientConfig = config.httpClient
  private val log = LoggerFactory.getLogger(classOf[APIController])

  @Action(method = "POST", path = "/api/nodes/notify")
  def notifyFromNode(node: JoinNodeRequest, response: HttpServletResponse): Unit = {
    config.corsHeader.foreach { cors =>
      if(cors.trim.length > 0){
        response.setHeader("Access-Control-Allow-Origin", cors)
      }
    }

    if(dataStore.existNode(node.url)){
      dataStore.updateNodeStatus(node.url, node.diskUsage)
    } else {
      dataStore.addNewNode(node.url, node.diskUsage, node.repos)
    }
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(response: HttpServletResponse): Seq[Node] = {
    config.corsHeader.foreach { cors =>
      if(cors.trim.length > 0){
        response.setHeader("Access-Control-Allow-Origin", cors)
      }
    }

    dataStore.allNodes().map { case (node, status) =>
      Node(node, status.diskUsage, status.repos)
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(response: HttpServletResponse): Seq[RepositoryInfo] = {
    config.corsHeader.foreach { cors =>
      if(cors.trim.length > 0){
        response.setHeader("Access-Control-Allow-Origin", cors)
      }
    }

    dataStore.allRepositories()
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}/_delete")
  def deleteRepositoryByPost(repositoryName: String, response: HttpServletResponse): Unit = {
    deleteRepository(repositoryName, response)
  }

  @Action(method = "DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String, response: HttpServletResponse): Unit = {
    config.corsHeader.foreach { cors =>
      if(cors.trim.length > 0){
        response.setHeader("Access-Control-Allow-Origin", cors)
      }
    }

    dataStore
      .getRepositoryStatus(repositoryName).map(_.nodes).getOrElse(Nil)
      .foreach { nodeUrl =>
        try {
          // Delete a repository from the node
          httpDelete[String](s"$nodeUrl/api/repos/$repositoryName")
          // Delete from NODE_REPOSITORY
          dataStore.deleteRepository(nodeUrl, repositoryName)
        } catch {
          case e: Exception => log.error(s"Failed to delete repository $repositoryName on $nodeUrl", e)
        }
      }

    // Delete from REPOSITORY
    dataStore.deleteRepository(repositoryName)
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}")
  def createRepository(repositoryName: String, response: HttpServletResponse): ActionResult[Unit] = {
    config.corsHeader.foreach { cors =>
      if(cors.trim.length > 0){
        response.setHeader("Access-Control-Allow-Origin", cors)
      }
    }

    val repo = dataStore.getRepositoryStatus(repositoryName)

    if(repo.nonEmpty){
      BadRequest(ErrorModel(Seq("Repository already exists.")))

    } else {
      val nodeUrls = dataStore.allNodes()
        .filter { case (_, status) => status.diskUsage < config.maxDiskUsage }
        .sortBy { case (_, status) => status.diskUsage }
        .take(config.replica)

      if(nodeUrls.nonEmpty){
        RepositoryLock.execute(repositoryName, "create repository"){
          // Insert to REPOSITORY and get timestamp
          val timestamp = dataStore.insertRepository(repositoryName)

          nodeUrls.foreach { case (nodeUrl, _) =>
            try {
              // Create a repository on the node
              httpPost(
                s"$nodeUrl/api/repos/${repositoryName}",
                Map.empty,
                builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
              )
              // Insert to NODE_REPOSITORY
              dataStore.insertNodeRepository(nodeUrl, repositoryName)

            } catch {
              case e: Exception =>
                log.error(s"Failed to create repository $repositoryName on $nodeUrl", e)
            }
          }

          Ok((): Unit)
        }
      } else {
        BadRequest(ErrorModel(Seq("There are no nodes which can accommodate a new repository")))
      }
    }
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}/_synced")
  def repositorySynchronized(repositoryName: String, request: SynchronizeRequest): Unit = {
    dataStore.insertNodeRepository(request.nodeUrl, repositoryName)
  }

}

object APIController {
  case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
  case class JoinNodeRepository(name: String, timestamp: Long)

  case class Node(url: String, diskUsage: Double, repos: Seq[String])
  case class NodeRepositoryInfo(name: String, status: String)

  case class SynchronizeRequest(nodeUrl: String)
}


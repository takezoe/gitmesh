package com.github.takezoe.gitmesh.controller

import javax.servlet.http.HttpServletResponse

import com.github.takezoe.resty._
import org.slf4j.LoggerFactory
import com.github.takezoe.gitmesh.controller.models.NodeRepositoryStatus

import api._

class APIController(dataStore: DataStore)(implicit val config: Config) extends HttpClientSupport {

  implicit override val httpClientConfig = config.httpClient
  private val log = LoggerFactory.getLogger(classOf[APIController])

  @Action(method = "POST", path = "/api/nodes/notify")
  def notifyFromNode(node: JoinNodeRequest, response: HttpServletResponse): Unit = {
    if(dataStore.existNode(node.url)){
      dataStore.updateNodeStatus(node.url, node.diskUsage)
    } else {
      dataStore.addNewNode(node.url, node.diskUsage, node.repos)
    }
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(response: HttpServletResponse): Seq[NodeStatus] = {
    dataStore.allNodes()
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(response: HttpServletResponse): Seq[RepositoryInfo] = {
    dataStore.allRepositories()
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}/_delete")
  def deleteRepositoryByPost(repositoryName: String, response: HttpServletResponse): Unit = {
    deleteRepository(repositoryName, response)
  }

  @Action(method = "DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String, response: HttpServletResponse): Unit = {
    dataStore
      .getRepositoryStatus(repositoryName).map(_.nodes).getOrElse(Nil)
      .foreach { node =>
        try {
          // Delete a repository from the node
          httpDelete[String](s"${node.url}/api/repos/$repositoryName")
          // Delete from NODE_REPOSITORY
          dataStore.deleteRepository(node.url, repositoryName)
        } catch {
          case e: Exception => log.error(s"Failed to delete repository $repositoryName on ${node.url}", e)
        }
      }

    // Delete from REPOSITORY
    dataStore.deleteRepository(repositoryName)
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}")
  def createRepository(repositoryName: String, response: HttpServletResponse): ActionResult[Unit] = {
    val repo = dataStore.getRepositoryStatus(repositoryName)

    if(repo.nonEmpty){
      BadRequest(ErrorModel(Seq("Repository already exists.")))

    } else {
      val nodes = dataStore.allNodes()
        .filter { _.diskUsage < config.maxDiskUsage }
        .sortBy { _.diskUsage }
        .take(config.replica)

      if(nodes.nonEmpty){
        RepositoryLock.execute(repositoryName, "create repository"){
          // Insert to REPOSITORY and get timestamp
          val timestamp = dataStore.insertRepository(repositoryName)

          nodes.foreach { node =>
            try {
              // Create a repository on the node
              httpPost(
                s"${node.url}/api/repos/${repositoryName}",
                Map.empty,
                builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
              )
              // Insert to NODE_REPOSITORY
              dataStore.insertNodeRepository(node.url, repositoryName, NodeRepositoryStatus.Ready)

            } catch {
              case e: Exception =>
                log.error(s"Failed to create repository $repositoryName on ${node.url}", e)
            }
          }

          Ok((): Unit)
        }
      } else {
        BadRequest(ErrorModel(Seq("There are no nodes which can accommodate a new repository")))
      }
    }
  }

  @SystemAPI
  @Action(method = "POST", path = "/api/repos/{repositoryName}/_synced")
  def repositorySynchronized(repositoryName: String, request: SynchronizedRequest): Unit = {
    dataStore.updateNodeRepository(request.nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
    RepositoryLock.unlock(repositoryName)
  }

  @SystemAPI
  @Action(method = "POST", path = "/api/repos/{repositoryName}/_lock")
  def lockRepository(repositoryName: String): Unit = {
    RepositoryLock.lock(repositoryName, "Lock by API")
  }

//  @SystemAPI
//  @Action(method = "POST", path = "/api/repos/{repositoryName}/_unlock")
//  def unlockRepository(repositoryName: String): Unit = {
//    RepositoryLock.unlock(repositoryName)
//  }

}


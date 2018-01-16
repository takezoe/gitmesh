package com.github.takezoe.dgit.controller

import com.github.takezoe.resty._
import org.slf4j.LoggerFactory

class APIController(config: Config, dataStore: DataStore) extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(classOf[APIController])

  @Action(method = "POST", path = "/api/nodes/notify")
  def notifyFromNode(node: JoinNodeRequest): Unit = {
    if(dataStore.existNode(node.url)){
      dataStore.updateNodeStatus(node.url, node.diskUsage)
    } else {
      dataStore.addNewNode(node.url, node.diskUsage, node.repos, config.replica)
    }
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = {
    dataStore.allNodes().map { case (node, status) =>
      Node(node, status.diskUsage, status.repos)
    }
  }

  @Action(method = "GET", path = "/api/repos")
  def listRepositories(): Seq[Repository] = {
    dataStore.allRepositories()
  }

  @Action(method = "DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String): Unit = {
    dataStore
      .getRepositoryStatus(repositoryName).map(_.nodes).getOrElse(Nil)
      .foreach { nodeUrl =>
        try {
          // Delete a repository from the node
          httpDelete[String](new SimpleRequestExecutor(s"$nodeUrl/api/repos/$repositoryName", Config.httpExecutorConfig))
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
  def createRepository(repositoryName: String): ActionResult[Unit] = {
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
                new SimpleRequestExecutor(s"$nodeUrl/api/repos/${repositoryName}", Config.httpExecutorConfig),
                Map.empty,
                builder => { builder.addHeader("DGIT-UPDATE-ID", timestamp.toString)
              })
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

}

case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
case class JoinNodeRepository(name: String, timestamp: Long)

case class Node(url: String, diskUsage: Double, repos: Seq[String])
case class NodeRepository(name: String, status: String)


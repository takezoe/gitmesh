package com.github.takezoe.dgit.controller

import com.github.takezoe.resty._

class APIController(config: Config) extends HttpClientSupport {

  @Action(method = "POST", path = "/api/nodes/join")
  def joinRepositoryNode(node: JoinNodeRequest): Unit = Database.withTransaction { implicit conn =>
    if(NodeManager.existNode(node.url)){
      NodeManager.updateNodeStatus(node.url, node.diskUsage)
    } else {
      NodeManager.addNewNode(node.url, node.diskUsage, node.repos, config.replica)
    }
  }

  @Action(method = "GET", path = "/api/nodes")
  def listNodes(): Seq[Node] = Database.withTransaction { implicit conn =>
    NodeManager.allNodes().map { case (node, status) =>
      Node(node, status.diskUsage, status.readyRepos.map(x => NodeRepository(x.repositoryName, x.status)))
    }
  }

//  @Action(method = "GET", path = "/api/repos")
//  def listRepositories(): Seq[Repository] = {
//    NodeManager.allRepositories()
//  }

  @Action(method = "DELETE", path = "/api/repos/{repositoryName}")
  def deleteRepository(repositoryName: String): Unit = Database.withTransaction { implicit conn =>
    NodeManager.getNodeUrlsOfRepository(repositoryName).foreach { nodeUrl =>
      httpDelete[String](s"$nodeUrl/api/repos/$repositoryName")
    }
    // TODO Should delete a record per node in isolated transaction?
    NodeManager.deleteRepository(repositoryName)
  }

  @Action(method = "POST", path = "/api/repos/{repositoryName}")
  def createRepository(repositoryName: String): ActionResult[Unit] = Database.withTransaction { implicit conn =>
    if(NodeManager.existRepository(repositoryName)){
      BadRequest(ErrorModel(Seq("Repository already exists.")))

    } else {
      val nodeUrls = NodeManager.allNodes()
        .filter { case (_, status) => status.diskUsage < config.maxDiskUsage }
        .sortBy { case (_, status) => status.diskUsage }
        .take(config.replica)

      if(nodeUrls.nonEmpty){
        nodeUrls.foreach { case (nodeUrl, status) =>
          httpPost(s"$nodeUrl/api/repos/${repositoryName}", Map.empty)
          NodeManager.createRepository(nodeUrl, repositoryName)
        }
        Ok((): Unit)
      } else {
        BadRequest(ErrorModel(Seq("There are no nodes which can accommodate a new repository")))
      }
    }
  }

}

case class JoinNodeRequest(url: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
case class JoinNodeRepository(name: String, timestamp: Long)

case class Node(url: String, diskUsage: Double, repos: Seq[NodeRepository])
case class NodeRepository(name: String, status: String)


package com.github.takezoe.gitmesh.controller

import com.github.takezoe.resty.HttpClientSupport
import org.slf4j.LoggerFactory
import com.github.takezoe.tranquil._
import com.github.takezoe.tranquil.Dialect.mysql
import models._

class DataStore extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)

  def existNode(nodeUrl: String): Boolean = Database.withConnection { conn =>
    val count = Nodes.filter(_.nodeUrl eq nodeUrl).count(conn)

    count match {
      case i if i > 0 => true
      case _ => false
    }
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[APIController.JoinNodeRepository])(implicit config: Config): Unit =
    Database.withConnection { conn =>
      log.info(s"Add new node: $nodeUrl")

      Database.withTransaction(conn) {
        Nodes.insert(Node(nodeUrl, System.currentTimeMillis, diskUsage)).execute(conn)
      }

      repos.foreach { repo =>
        RepositoryLock.execute(repo.name, "add node"){
          Database.withTransaction(conn){
            getRepositoryStatus(repo.name) match  {
              case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < config.replica =>
                if(x.primaryNode.isEmpty){
                  Repositories.update(_.primaryNode -> nodeUrl).filter(_.repositoryName eq repo.name).execute(conn)
                }
                NodeRepositories.insert(NodeRepository(nodeUrl, repo.name)).execute(conn)
              case _ =>
                try {
                  httpDelete(s"$nodeUrl/api/repos/${repo.name}") // TODO Check left?
                } catch {
                  case e: Exception => log.error(s"Failed to delete repository ${repo.name} from $nodeUrl", e)
                }
            }
          }
        }
      }
    }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): Unit = Database.withConnection { conn =>
    Database.withTransaction(conn){
      Nodes
        .update(t => (t.lastUpdateTime -> System.currentTimeMillis) ~ (t.diskUsage -> diskUsage))
        .filter(_.nodeUrl eq nodeUrl)
        .execute(conn)
    }
  }

  def removeNode(nodeUrl: String)(implicit config: Config): Unit = Database.withConnection { conn =>
    log.info(s"Remove node: $nodeUrl")
    val repos = Repositories.filter(_.primaryNode eq nodeUrl).map(_.repositoryName).list(conn)

    repos.foreach { repositoryName =>
      RepositoryLock.execute(repositoryName, "remove node"){
        Database.withTransaction(conn){
          val nextPrimaryNodeUrl = NodeRepositories.filter { t =>
            (t.nodeUrl ne nodeUrl) && (t.repositoryName eq repositoryName)
          }.map(_.nodeUrl).firstOption(conn)

          nextPrimaryNodeUrl match {
            case Some(nodeUrl) =>
              Repositories.update(_.primaryNode -> nodeUrl).filter(_.repositoryName eq repositoryName).execute(conn)
            case None =>
              Repositories.update(_.primaryNode asNull).filter(_.repositoryName eq repositoryName).execute(conn)
              log.error(s"All nodes for $repositoryName has been retired.")
          }

          NodeRepositories.delete().filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
        }
      }
    }

    Database.withTransaction(conn){
      NodeRepositories.delete().filter(_.nodeUrl eq nodeUrl).execute(conn)
      Nodes.delete().filter(_.nodeUrl eq nodeUrl).execute(conn)
    }
  }

  def allNodes(): Seq[(String, NodeStatus)] = Database.withConnection { conn =>
    Nodes
      .leftJoin(NodeRepositories){ case node ~ nodeRepository => node.nodeUrl eq nodeRepository.nodeUrl }
      .list(conn)
      .groupBy { case node ~ _ => node.nodeUrl }
      .map { case (nodeUrl, seq) =>
        val node = seq.head._1
        val repos = if(seq.head._2.isEmpty) Nil else seq.map(_._2.map(_.repositoryName).get)
        (nodeUrl, NodeStatus(node.lastUpdateTime, node.diskUsage, repos))
      }
      .toSeq
      .sortBy(_._1)
  }

  /**
   * NOTE: This method must be used only in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): Unit = Database.withConnection { conn =>
    Database.withTransaction(conn){
      Repositories.update(_.lastUpdateTime -> timestamp).filter(_.repositoryName eq repositoryName).execute(conn)
    }
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): Unit = Database.withConnection { conn =>
    Database.withTransaction(conn){
      Repositories.update(_.primaryNode asNull).filter(_.repositoryName eq repositoryName).execute(conn)
      NodeRepositories.delete().filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
    }
  }

  def deleteRepository(repositoryName: String): Unit = Database.withConnection { conn =>
    Database.withTransaction(conn){
      Repositories.delete().filter(_.repositoryName eq repositoryName).execute(conn)
    }
  }

  def insertRepository(repositoryName: String): Long = Database.withConnection { conn =>
    Database.withTransaction(conn){
      val timestamp = System.currentTimeMillis
      Repositories.insert(Repository(repositoryName, None, timestamp)).execute(conn)
      timestamp
    }
  }

  def insertNodeRepository(nodeUrl: String, repositoryName: String): Unit = Database.withConnection { conn =>
    Database.withTransaction(conn){
      if(getRepositoryStatus(repositoryName).map(_.primaryNode.isEmpty).getOrElse(false)){
        Repositories.update(_.primaryNode -> nodeUrl).filter(_.repositoryName eq repositoryName).execute(conn)
      }
      NodeRepositories.delete().filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
      NodeRepositories.insert(NodeRepository(nodeUrl, repositoryName)).execute(conn)
    }
  }

  def getRepositoryStatus(repositoryName: String): Option[RepositoryInfo] = Database.withConnection { conn =>
    val repos = Repositories.filter(_.repositoryName eq repositoryName).firstOption(conn)

    repos.map { repo =>
      val nodes = NodeRepositories.filter(_.repositoryName eq repositoryName).map(_.nodeUrl).list(conn)
      RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes)
    }
  }

  def allRepositories(): Seq[RepositoryInfo] = Database.withConnection { conn =>
    Repositories
      .leftJoin(NodeRepositories){ case repo ~ node => repo.repositoryName eq node.repositoryName }
      .list(conn)
      .groupBy { case repo ~ node => repo.repositoryName }
      .map { case (repositoryName, seq) =>
        val repo = seq.head._1
        val nodes = if(seq.head._2.isEmpty) Nil else seq.map(_._2.map(_.nodeUrl).get)
        RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes)
      }
      .toSeq
      .sortBy(_.name)
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit config: Config): Option[String] = Database.withConnection { conn =>
    Nodes.filter(t =>
      (t.nodeUrl notIn (NodeRepositories.filter(_.repositoryName eq repositoryName).map(_.nodeUrl))) &&
      (t.diskUsage le config.maxDiskUsage)
    ).sortBy(_.diskUsage asc).map(_.nodeUrl).firstOption(conn)
  }

}

case class RepositoryInfo(name: String, primaryNode: Option[String], timestamp: Long, nodes: Seq[String])
case class RepositoryNode(nodeUrl: String, status: String)

case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[String])

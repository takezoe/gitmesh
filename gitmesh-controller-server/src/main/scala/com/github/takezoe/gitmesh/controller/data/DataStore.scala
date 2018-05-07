package com.github.takezoe.gitmesh.controller.data

import cats.effect.IO
import cats.implicits._
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import com.github.takezoe.tranquil.Dialect.mysql
import com.github.takezoe.tranquil._
import org.slf4j.LoggerFactory

class DataStore {

  private val log = LoggerFactory.getLogger(getClass)

  def existNode(nodeUrl: String): IO[Boolean] = IO {
    Database.withConnection { conn =>
      val count = Nodes.filter(_.nodeUrl eq nodeUrl).count(conn)

      count match {
        case i if i > 0 => true
        case _ => false
      }
    }
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
                (implicit config: Config): IO[Seq[(JoinNodeRepository, Boolean)]] = IO {
    Database.withConnection { conn =>
      log.info(s"Add new node: $nodeUrl")

      Database.withTransaction(conn) {
        Nodes.insert(Node(nodeUrl, System.currentTimeMillis, diskUsage)).execute(conn)
      }

      repos.map { repo =>
        RepositoryLock.execute(repo.name, "add node") {
          Database.withTransaction(conn) {
            getRepositoryStatus(repo.name).unsafeRunSync() match {
              case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < config.replica =>
                if (x.primaryNode.isEmpty) {
                  Repositories.update(_.primaryNode -> nodeUrl).filter(_.repositoryName eq repo.name).execute(conn)
                }
                NodeRepositories.insert(NodeRepository(nodeUrl, repo.name, NodeRepositoryStatus.Ready)).execute(conn)
                (repo, true) // added
              case _ =>
                (repo, false) // not added
            }
          }
        }
      }
    }
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        Nodes
          .update(t => (t.lastUpdateTime -> System.currentTimeMillis) ~ (t.diskUsage -> diskUsage))
          .filter(_.nodeUrl eq nodeUrl)
          .execute(conn)
      }
    }
  }

  def removeNode(nodeUrl: String)(implicit config: Config): IO[Unit] = IO {
    Database.withConnection { conn =>
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
  }

  def allNodes(): IO[Seq[NodeStatus]] = IO {
    Database.withConnection { conn =>
      Nodes
        .leftJoin(NodeRepositories){ case node ~ nodeRepository => node.nodeUrl eq nodeRepository.nodeUrl }
        .list(conn)
        .groupBy { case node ~ _ => node.nodeUrl }
        .map { case (nodeUrl, seq) =>
          val node = seq.head._1
          val repos = if(seq.head._2.isEmpty) Nil else seq.flatMap(_._2.map(x => NodeStatusRepository(x.repositoryName, x.status)))
          NodeStatus(nodeUrl, node.lastUpdateTime, node.diskUsage, repos)
        }
        .toSeq
        .sortBy(_.url)
    }
  }

  /**
   * NOTE: This method must be used only in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        Repositories.update(_.lastUpdateTime -> timestamp).filter(_.repositoryName eq repositoryName).execute(conn)
      }
    }
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        Repositories.update(_.primaryNode asNull).filter(_.repositoryName eq repositoryName).execute(conn)
        NodeRepositories.delete().filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
      }
    }
  }

  def deleteRepository(repositoryName: String): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        Repositories.delete().filter(_.repositoryName eq repositoryName).execute(conn)
      }
    }
  }

  def insertRepository(repositoryName: String): IO[Long] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        val timestamp = InitialRepositoryId
        Repositories.insert(Repository(repositoryName, None, timestamp)).execute(conn)
        timestamp
      }
    }
  }

  def insertNodeRepository(nodeUrl: String, repositoryName: String, status: String): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn){
        val repo = getRepositoryStatus(repositoryName).unsafeRunSync()
        if(repo.map(_.primaryNode.isEmpty).getOrElse(false)){
          Repositories.update(_.primaryNode -> nodeUrl).filter(_.repositoryName eq repositoryName).execute(conn)
        }
        NodeRepositories.delete().filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
        NodeRepositories.insert(NodeRepository(nodeUrl, repositoryName, status)).execute(conn)
      }
    }
  }

  def updateNodeRepository(nodeUrl: String, repositoryName: String, status: String): IO[Unit] = IO {
    Database.withConnection { conn =>
      Database.withTransaction(conn) {
        NodeRepositories.update(_.status -> status).filter(t => (t.nodeUrl eq nodeUrl) && (t.repositoryName eq repositoryName)).execute(conn)
      }
    }
  }

  def getRepositoryStatus(repositoryName: String): IO[Option[RepositoryInfo]] = IO {
    Database.withConnection { conn =>
      val repos = Repositories.filter(_.repositoryName eq repositoryName).firstOption(conn)

      repos.map { repo =>
        val nodes = NodeRepositories.filter(_.repositoryName eq repositoryName).list(conn)
        RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes.map(x => RepositoryNodeInfo(x.nodeUrl, x.status)))
      }
    }
  }

  def allRepositories(): IO[Seq[RepositoryInfo]] = IO {
    Database.withConnection { conn =>
      Repositories
        .leftJoin(NodeRepositories){ case repo ~ node => repo.repositoryName eq node.repositoryName }
        .list(conn)
        .groupBy { case repo ~ node => repo.repositoryName }
        .map { case (repositoryName, seq) =>
          val repo = seq.head._1
          val nodes = if(seq.head._2.isEmpty) Nil else seq.flatMap(_._2.map(x => RepositoryNodeInfo(x.nodeUrl, x.status)))
          RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes)
        }
        .toSeq
        .sortBy(_.name)
    }
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit config: Config): IO[Option[String]] = IO {
    Database.withConnection { conn =>
      Nodes.filter(t =>
        (t.nodeUrl notIn (NodeRepositories.filter(_.repositoryName eq repositoryName).map(_.nodeUrl))) &&
          (t.diskUsage le config.maxDiskUsage)
      ).sortBy(_.diskUsage asc).map(_.nodeUrl).firstOption(conn)
    }
  }

}

package com.github.takezoe.gitmesh.controller.data

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import com.github.takezoe.tranquil.Dialect.mysql
import com.github.takezoe.tranquil._
import org.slf4j.LoggerFactory
//import cats._, cats.data._
import doobie._
import doobie.implicits._
import cats.implicits._

class DataStore {

  private val log = LoggerFactory.getLogger(getClass)

  def existNode(nodeUrl: String): IO[Boolean] = {
    (for {
      count <- sql"SELECT COUNT(*) FROM NODE WHERE NODE_URL = $nodeUrl".query[Int].unique
    } yield {
      count match {
        case i if i > 0 => true
        case _          => false
      }
    }).transact(Database.transactor)
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
                (implicit config: Config): IO[Seq[(JoinNodeRepository, Boolean)]] = {
    for {
      _      <- (for {
        result <- sql"INSERT INTO NODE (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE) VALUES ($nodeUrl, ${System.currentTimeMillis}, $diskUsage)".update.run
      } yield result).transact(Database.transactor)
      result <- ({
        repos.map { repo =>
          for {
            status <- getRepositoryStatus(repo.name)
            result <- status match {
              // add
              case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < config.replica =>
                (for {
                  _      <- if(x.primaryNode.isEmpty){
                             sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = ${repo.name}".update.run
                           } else 0.pure[ConnectionIO]
                  _      <- sql"INSERT INTO NODE_REPOSITORY () VALUES()".update.run
                  result <- (repo, true).pure[ConnectionIO]
                } yield result).transact(Database.transactor)
              // not add
              case _ => IO.pure(repo, false)
            }
          } yield result
        }
      }).toList.sequence
    } yield result
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): IO[Unit] = {
    (for {
      _      <- sql"UPDATE NODE SET LAST_UPDATE_TIME = ${System.currentTimeMillis}, DISK_USAGE = $diskUsage WHERE NODE_URL = $nodeUrl".update.run
      result <- ().pure[ConnectionIO]
    } yield result).transact(Database.transactor)
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

  def allNodes(): IO[Seq[NodeStatus]] = {
    (for {
      nodes <- sql"""SELECT N.NODE_URL,
                            N.LAST_UPDATE_TIME,
                            N.DISK_USAGE,
                            NR.NODE_URL,
                            NR.REPOSITORY_NAME,
                            NR.STATUS
                     FROM NODE N
                     LEFT JOIN NODE_REPOSITORY NR ON N.NODE_URL = NR.NODE_URL"""
        .query[(Node, Option[String], Option[String], Option[String])]
        .map { case (node, nodeUrl, repositoryName, status) => (node, (nodeUrl, repositoryName, status).mapN(NodeRepository)) }
        .to[List]
      result <- nodes
        .groupBy { case (node, _) => node.nodeUrl }
        .collect { case (_, nodes @ (node, _) :: _) =>
          NodeStatus(
            url       = node.nodeUrl,
            timestamp = node.lastUpdateTime,
            diskUsage = node.diskUsage,
            repos     = nodes.collect { case (_, Some(x)) => NodeStatusRepository(x.repositoryName, x.status) }
          )
        }.pure[ConnectionIO]
    } yield result.toSeq).transact(Database.transactor)
  }

  /**
   * NOTE: This method must be used only in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): IO[Unit] = {
    (for {
      _      <- sql"UPDATE REPOSITORY SET LAST_UPDATE_TIME = $timestamp WHERE REPOSITORY_NAME = $repositoryName".update.run
      result <- ().pure[ConnectionIO]
    } yield result).transact(Database.transactor)
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): IO[Unit] = {
    (for {
      _      <- sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE REPOSITORY_NAME = $repositoryName".update.run
      _      <- sql"DELETE NODE_REPOSITORY WHERE NODE_URL = $nodeUrl".update.run
      result <- ().pure[ConnectionIO]
    } yield result).transact(Database.transactor)
  }

  def deleteRepository(repositoryName: String): IO[Unit] = {
    (for {
      _      <- sql"DELETE REPOSITORY WHERE REPOSITORY_NAME = $repositoryName".update.run
      result <- ().pure[ConnectionIO]
    } yield result).transact(Database.transactor)
  }

  def insertRepository(repositoryName: String): IO[Long] = {
    (for {
      timestamp <- InitialRepositoryId.pure[ConnectionIO]
      _         <- sql"INSERT INTO REPOSITORY (REPOSITORY_NAME, PRIMARY_NODE, STATUS) VALUES ($repositoryName, $timestamp, NULL)".update.run
    } yield timestamp).transact(Database.transactor)
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

  def updateNodeRepository(nodeUrl: String, repositoryName: String, status: String): IO[Unit] = {
    (for {
      _      <- sql"UPDATE NODE_REPOSITORY SET STATUS = $status WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName".update.run
      result <- ().pure[ConnectionIO]
    } yield result).transact(Database.transactor)
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

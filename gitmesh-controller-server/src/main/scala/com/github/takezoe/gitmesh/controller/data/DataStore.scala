package com.github.takezoe.gitmesh.controller.data

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import org.slf4j.LoggerFactory
import doobie._
import doobie.implicits._
import cats.implicits._

class DataStore {

  private val log = LoggerFactory.getLogger(getClass)

  def clearClusterStatus(): IO[Unit] = {
    (for {
      _ <- sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL".update.run
      _ <- sql"DELETE FROM NODE_REPOSITORY".update.run
      _ <- sql"DELETE FROM NODE".update.run
      _ <- sql"DELETE FROM EXCLUSIVE_LOCK".update.run
    } yield ()).transact(Database.xa)
  }

  def existNode(nodeUrl: String): IO[Boolean] = {
    (for {
      count <- sql"SELECT COUNT(*) FROM NODE WHERE NODE_URL = $nodeUrl".query[Int].unique
    } yield {
      count match {
        case i if i > 0 => true
        case _          => false
      }
    }).transact(Database.xa)
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
                (implicit config: Config): IO[Seq[(JoinNodeRepository, Boolean)]] = {
    for {
      _      <- sql"INSERT INTO NODE (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE) VALUES ($nodeUrl, ${System.currentTimeMillis}, $diskUsage)".update.run.transact(Database.xa)
      result <- ({
        repos.map { repo =>
          for {
            status <- getRepositoryStatus(repo.name)
            result <- status match {
              // add
              case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < config.replica =>
                (for {
                  _ <- if(x.primaryNode.isEmpty){
                         sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = ${repo.name}".update.run
                       } else 0.pure[ConnectionIO]
                  _ <- sql"INSERT INTO NODE_REPOSITORY (NODE_URL, REPOSITORY_NAME, STATUS) VALUES($nodeUrl, ${repo.name}, ${NodeRepositoryStatus.Ready})".update.run
                } yield (repo, true)).transact(Database.xa)
              // not add
              case _ => IO.pure((repo, false))
            }
          } yield result
        }
      }).toList.sequence
    } yield result
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): IO[Unit] = {
    (for {
      _ <- sql"UPDATE NODE SET LAST_UPDATE_TIME = ${System.currentTimeMillis}, DISK_USAGE = $diskUsage WHERE NODE_URL = $nodeUrl".update.run
    } yield ()).transact(Database.xa)
  }

  def removeNode(nodeUrl: String)(implicit config: Config): IO[Unit] = {
    log.info(s"Remove node: $nodeUrl")
    for {
      repos <- sql"SELECT REPOSITORY_NAME FROM REPOSITORY WHERE PRIMARY_NODE = $nodeUrl".query[String].to[List].transact(Database.xa)
      _     <- repos.map { repositoryName =>
        RepositoryLock.execute(repositoryName, "remove node") {
          (for {
            next <- sql"SELECT NODE_URL FROM NODE_REPOSITORY WHERE NODE_URL <> $nodeUrl AND REPOSITORY_NAME = $repositoryName LIMIT 1".query[String].option
            _    <- next match {
              case Some(next) =>
                sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName".update.run
              case None =>
                log.error(s"All nodes for $repositoryName has been retired.")
                sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE REPOSITORY_NAME = $repositoryName".update.run
            }
          } yield ()).transact(Database.xa)
        }
      }.sequence
      _ <- (for {
        _ <- sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl".update.run
        _ <- sql"DELETE FROM NODE WHERE NODE_URL = $nodeUrl".update.run
      } yield()).transact(Database.xa)
    } yield ()
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
        .query[(Node, Option[NodeRepository])].to[List]
    } yield {
      nodes
        .groupBy { case (node, _) => node }
        .collect { case (node, nodes) =>
          NodeStatus(
            url       = node.nodeUrl,
            timestamp = node.lastUpdateTime,
            diskUsage = node.diskUsage,
            repos     = nodes.collect { case (_, Some(x)) => NodeStatusRepository(x.repositoryName, x.status) }
          )
        }.toSeq
    }).transact(Database.xa)
  }

  /**
   * NOTE: This method must be used only in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): IO[Unit] = {
    (for {
      _ <- sql"UPDATE REPOSITORY SET LAST_UPDATE_TIME = $timestamp WHERE REPOSITORY_NAME = $repositoryName".update.run
    } yield ()).transact(Database.xa)
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): IO[Unit] = {
    (for {
      _ <- sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE REPOSITORY_NAME = $repositoryName".update.run
      _ <- sql"DELETE NODE_REPOSITORY WHERE NODE_URL = $nodeUrl".update.run
    } yield ()).transact(Database.xa)
  }

  def deleteRepository(repositoryName: String): IO[Unit] = {
    (for {
      _ <- sql"DELETE REPOSITORY WHERE REPOSITORY_NAME = $repositoryName".update.run
    } yield ()).transact(Database.xa)
  }

  def insertRepository(repositoryName: String): IO[Long] = {
    (for {
      timestamp <- InitialRepositoryId.pure[ConnectionIO]
      _         <- sql"INSERT INTO REPOSITORY (REPOSITORY_NAME, PRIMARY_NODE, STATUS) VALUES ($repositoryName, $timestamp, NULL)".update.run
    } yield timestamp).transact(Database.xa)
  }

  def insertNodeRepository(nodeUrl: String, repositoryName: String, status: String): IO[Unit] = {
    for {
      repo <- getRepositoryStatus(repositoryName)
      _    <- (for {
        _ <- if(repo.map(_.primaryNode.isEmpty).getOrElse(false)){
          sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName".update.run
        } else ().pure[ConnectionIO]
        _ <- sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName".update.run
        _ <- sql"INSERT INTO NODE_REPOSITORY (NODE_URL, REPOSITORY_NAME, STATUS) VALUES ($nodeUrl, $repositoryName, $status)".update.run
      } yield ()).transact(Database.xa)
    } yield ()
  }

  def updateNodeRepository(nodeUrl: String, repositoryName: String, status: String): IO[Unit] = {
    (for {
      _ <- sql"UPDATE NODE_REPOSITORY SET STATUS = $status WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName".update.run
    } yield ()).transact(Database.xa)
  }

  def getRepositoryStatus(repositoryName: String): IO[Option[RepositoryInfo]] = {
    (for {
      repo  <- sql"SELECT REPOSITORY_NAME, PRIMARY_NODE, LAST_UPDATE_TIME FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName".query[Repository].option
      nodes <- sql"SELECT NODE_URL, REPOSITORY_NAME, STATUS FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName".query[NodeRepository].to[List]
    } yield {
      repo.map { repo =>
        RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes.map(x => RepositoryNodeInfo(x.nodeUrl, x.status)))
      }
    }).transact(Database.xa)
  }

  def allRepositories(): IO[Seq[RepositoryInfo]] = {
    (for {
      result <-
        sql"""SELECT
                R.REPOSITORY_NAME,
                R.PRIMARY_NODE,
                R.LAST_UPDATE_TIME,
                NR.NODE_URL,
                NR.REPOSITORY_NAME,
                NR.STATUS
              FROM REPOSITORY R
              LEFT OUTER JOIN NODE_REPOSITORY NR ON R.REPOSITORY_NAME = NR.REPOSITORY_NAME"""
          .query[(Repository, Option[NodeRepository])].to[List]
    } yield {
      result
        .groupBy { case (repo, _) => repo }
        .collect { case (repo, nodes) =>
          RepositoryInfo(
            name        = repo.repositoryName,
            primaryNode = repo.primaryNode,
            timestamp   = repo.lastUpdateTime,
            nodes       = nodes.collect { case (_, Some(node)) => RepositoryNodeInfo(node.nodeUrl, node.status) }
          )
        }.toSeq
    }).transact(Database.xa)
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit config: Config): IO[Option[String]] = {
    (for {
      node <- sql"""SELECT N.NODE_URL
                    FROM NODE N
                    WHERE
                      N.NODE_URL NOT IN (
                        SELECT NODE_URL
                        FROM NODE_REPOSITORY NR
                        WHERE NR.REPOSITORY_NAME = $repositoryName
                      ) AND N.DISK_USAGE < ${config.maxDiskUsage}
                    ORDER BY N.DISK_USAGE
                    LIMIT 1""".query[String].option
    } yield node).transact(Database.xa)
  }

}

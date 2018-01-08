package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

import com.github.takezoe.resty.HttpClientSupport

import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import com.github.takezoe.scala.jdbc._
import syntax._

// TODO Should be a class?
object NodeManager extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)

  def updateNodeStatus(nodeUrl: String, diskUsage: Double, repos: Seq[String]): Unit = {
    Database.withTransaction { conn =>
      defining(DB(conn)){ db =>
        val existNodeUrl = db.selectFirst(
          sql"SELECT NODE_URL FROM REPOSITORY_NODE_STATUS WHERE NODE_URL = $nodeUrl"
        ){ rs => rs.getString("NODE_URL") }

        existNodeUrl match {
          // Update node status
          case Some(nodeUrl) =>
            db.update(sql"""
               UPDATE REPOSITORY_NODE_STATUS SET
                 LAST_UPDATE_TIME = ${System.currentTimeMillis()},
                 DISK_USAGE       = $diskUsage
               WHERE NODE_URL = $nodeUrl
            """)
          // Insert node records
          case None =>
            db.update(sql"""
              INSERT INTO REPOSITORY_NODE_STATUS
                (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE)
              VALUES
                ($nodeUrl, ${System.currentTimeMillis()}, $diskUsage)
            """)
            // TODO Ignore if the repository is not registered in REPOSITORY table
            repos.foreach { repositoryName =>
              db.update(sql"""
                INSERT INTO REPOSITORY_NODE
                  (NODE_URL, REPOSITORY_NAME, STATUS)
                VALUES
                  ($nodeUrl, $repositoryName, 'preparing')
              """)
            }
        }

      }
    }
  }

  def removeNode(nodeUrl: String): Unit = {
    Database.withTransaction { conn =>
      defining(DB(conn)){ db =>
        // Update primary repository
        val repos = db.select(sql"SELECT REPOSITORY_NAME FROM REPOSITORY WHERE PRIMARY_NODE = $nodeUrl "){ rs =>
          rs.getString("REPOSITORY_NAME")
        }

        repos.foreach { repositoryName =>
          val nextPrimaryNodeUrl = db.selectFirst[String](sql"""
            SELECT N.NODE_URL AS NODE_URL
            FROM REPOSITORY_NODE N
            INNER JOIN REPOSITORY_NODE_STATUS S ON N.NODE_URL = S.NODE_URL
            WHERE N.REPOSITORY_NAME = $repositoryName AND N.STATUS = 'synced'
            ORDER BY S.LAST_UPDATE_TIME DESC
          """)(_.getString("NODE_URL"))

          nextPrimaryNodeUrl match {
            case Some(nodeUrl) =>
              db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName")
            case None =>
              db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE REPOSITORY_NAME = $repositoryName")
              log.error(s"All nodes for $repositoryName has been retired.")
          }
        }

        // Delete node records
        db.update(sql"DELETE FROM REPOSITORY_NODE")
        db.update(sql"DELETE FROM REPOSITORY_NODE_STATUS")
      }
    }
  }

  def allNodes(): Seq[(String, NodeStatus)] = {
    Database.withTransaction { conn =>
      defining(DB(conn)){ db =>
        db.select(sql"SELECT NODE_URL, LAST_UPDATE_TIME, DISK_USAGE FROM REPOSITORY_NODE_STATUS"){ rs =>
          val nodeUrl   = rs.getString("NODE_URL")
          val timestamp = rs.getLong("LAST_UPDATE_TIME")
          val diskUsage = rs.getDouble("DISK_USAGE")
          val repos     = db.select(
            sql"SELECT REPOSITORY_NAME, STATUS FROM REPOSITORY_NODE WHERE NODE_URL = $nodeUrl"
          ){ rs => RepositoryStatus(rs.getString("REPOSITORY_NAME"), rs.getString("STATUS")) }

          (nodeUrl, NodeStatus(timestamp, diskUsage, repos))
        }
      }
    }
  }

  def getNodeStatus(nodeUrl: String): Option[NodeStatus] = {
    Database.withTransaction { conn =>
      defining(DB(conn)){ db =>
        db.selectFirst(sql"SELECT NODE_URL, LAST_UPDATED_TIME, DISK_USAGE FROM REPOSITORY_NODE_STATUS WHERE NODE_URL = $nodeUrl"){ rs =>
          val repos = db.select(sql"SELECT REPOSITORY_NAME, STATUS FROM REPOSITORY_NODE WHERE NODE_URL = $nodeUrl"){ rs =>
            RepositoryStatus(rs.getString("REPOSITORY_NAME"), rs.getString("STATUS"))
          }
          NodeStatus(rs.getLong("LAST_UPDATED_TIME"), rs.getDouble("DISK_USAGE"), repos)
        }
      }
    }
  }

//  def getUrlOfPrimaryNode(repositoryName: String): Option[String] = {
//    Option(primaryNodeOfRepository.get(repositoryName))
//  }
//
//  def getNodeUrlsOfRepository(repositoryName: String): Seq[String] = {
//    nodes.asScala.collect { case (nodeUrl, status) if status.repos.contains(repositoryName) => nodeUrl }.toSeq
//  }
//
//  def getUrlOfAvailableNode(repositoryName: String): Option[String] = {
//    nodes.asScala.collectFirst { case (nodeUrl, status) if !status.repos.contains(repositoryName) => nodeUrl }
//  }
//
//  def allRepositories(): Seq[Repository] = {
//    nodes.asScala.toSeq
//      .flatMap { case (nodeUrl, status) => status.repos.map { repositoryName => (repositoryName, nodeUrl) } }
//      .groupBy { case (repositoryName, _) => repositoryName }
//      .map { case (repositoryName, group) =>
//        Repository(
//          repositoryName,
//          getUrlOfPrimaryNode(repositoryName).get, // TODO Don't use Option.get!
//          group.map { case (_, nodeUrl) => nodeUrl }
//        )
//      }
//      .toSeq
//  }

}

case class Repository(name: String, primaryNode: String, nodes: Seq[String])
case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[RepositoryStatus])
case class RepositoryStatus(repositoryName: String, status: String)
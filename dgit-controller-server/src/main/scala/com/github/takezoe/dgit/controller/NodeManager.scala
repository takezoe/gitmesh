package com.github.takezoe.dgit.controller

import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

import com.github.takezoe.resty.HttpClientSupport

import org.slf4j.LoggerFactory
import com.github.takezoe.scala.jdbc._
import syntax._

// TODO Should be a class?
object NodeManager extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)

  val RepositoryStatusEnabled = "enabled"
  val RepositoryStatusDisabled = "disabled"

  def existRepository(repositoryName: String)(implicit conn: Connection): Boolean = {
    defining(DB(conn)){ db =>
      val count = db.selectFirst(
        sql"SELECT COUNT(*) AS COUNT FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"
      ){ rs => rs.getInt("COUNT") }

      count match {
        case Some(i) if i > 0 => true
        case _ => false
      }
    }
  }

  def existNode(nodeUrl: String)(implicit conn: Connection): Boolean = {
    defining(DB(conn)){ db =>
      val count = db.selectFirst(
        sql"SELECT COUNT(*) AS COUNT FROM REPOSITORY_NODE_STATUS WHERE NODE_URL = $nodeUrl"
      ){ rs => rs.getInt("COUNT") }

      count match {
        case Some(i) if i > 0 => true
        case _ => false
      }
    }
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[String])(implicit conn: Connection): Unit = {
    log.info(s"Add new node: $nodeUrl")
    defining(DB(conn)){ db =>
      db.update(sql"""
              INSERT INTO REPOSITORY_NODE_STATUS
                (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE)
              VALUES
                ($nodeUrl, ${System.currentTimeMillis()}, $diskUsage)
            """)

      repos.foreach { repositoryName =>
        if(existRepository(repositoryName)){
          db.update(sql"""
                INSERT INTO REPOSITORY_NODE
                  (NODE_URL, REPOSITORY_NAME, STATUS)
                VALUES
                  ($nodeUrl, $repositoryName, $RepositoryStatusDisabled)
              """)
        }
      }
    }
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double)(implicit conn: Connection): Unit = {
    log.info(s"Update node status: $nodeUrl")
    defining(DB(conn)){ db =>
      db.update(sql"""
         UPDATE REPOSITORY_NODE_STATUS SET
           LAST_UPDATE_TIME = ${System.currentTimeMillis()},
           DISK_USAGE       = $diskUsage
         WHERE NODE_URL = $nodeUrl
      """)
    }
  }

  def removeNode(nodeUrl: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      // Delete node records
      db.update(sql"DELETE FROM REPOSITORY_NODE")
      db.update(sql"DELETE FROM REPOSITORY_NODE_STATUS")

      // Update primary repository
      val repos = db.select(sql"SELECT REPOSITORY_NAME FROM REPOSITORY WHERE PRIMARY_NODE = $nodeUrl "){ rs =>
        rs.getString("REPOSITORY_NAME")
      }

      repos.foreach { repositoryName =>
        val nextPrimaryNodeUrl = db.selectFirst[String](sql"""
          SELECT N.NODE_URL AS NODE_URL
          FROM REPOSITORY_NODE N
          INNER JOIN REPOSITORY_NODE_STATUS S ON N.NODE_URL = S.NODE_URL
          WHERE N.REPOSITORY_NAME = $repositoryName AND N.STATUS = $RepositoryStatusEnabled
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
    }
  }

  def allNodes()(implicit conn: Connection): Seq[(String, NodeStatus)] = {
    defining(DB(conn)){ db =>
      db.select(sql"SELECT NODE_URL, LAST_UPDATE_TIME, DISK_USAGE FROM REPOSITORY_NODE_STATUS"){ rs =>
        val nodeUrl   = rs.getString("NODE_URL")
        val timestamp = rs.getLong("LAST_UPDATE_TIME")
        val diskUsage = rs.getDouble("DISK_USAGE")
        val repos     = db.select(
          sql"SELECT REPOSITORY_NAME, STATUS FROM REPOSITORY_NODE WHERE NODE_URL = $nodeUrl"
        ){ rs => NodeStatusRepository(rs.getString("REPOSITORY_NAME"), rs.getString("STATUS")) }

        (nodeUrl, NodeStatus(timestamp, diskUsage, repos))
      }
    }
  }

  def getNodeStatus(nodeUrl: String)(implicit conn: Connection): Option[NodeStatus] = {
    defining(DB(conn)){ db =>
      db.selectFirst(sql"SELECT NODE_URL, LAST_UPDATED_TIME, DISK_USAGE FROM REPOSITORY_NODE_STATUS WHERE NODE_URL = $nodeUrl"){ rs =>
        // TODO Avoid N + 1 queries...
        val repos = db.select(sql"SELECT REPOSITORY_NAME, STATUS FROM REPOSITORY_NODE WHERE NODE_URL = $nodeUrl"){ rs =>
          NodeStatusRepository(rs.getString("REPOSITORY_NAME"), rs.getString("STATUS"))
        }
        NodeStatus(rs.getLong("LAST_UPDATED_TIME"), rs.getDouble("DISK_USAGE"), repos)
      }
    }
  }

  def getUrlOfPrimaryNode(repositoryName: String)(implicit conn: Connection): Option[String] = {
    defining(DB(conn)){ db =>
      db.selectFirst(sql"SELECT PRIMARY_NODE FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
        rs.getString("PRIMARY_NODE")
      }
    }
  }

  def getNodeUrlsOfRepository(repositoryName: String)(implicit conn: Connection): Seq[String] = {
    defining(DB(conn)){ db =>
      db.select(sql"SELECT NODE_URL FROM REPOSITORY_NODE WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
        rs.getString("NODE_URL")
      }
    }
  }

  def deleteRepository(repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      db.update(sql"DELETE FROM REPOSITORY_NODE WHERE REPOSITORY_NAME = $repositoryName")
      db.update(sql"DELETE FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName")
    }
  }

  def createRepository(nodeUrl: String, repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      if(!existRepository(repositoryName)){
        db.update(sql"INSERT INTO REPOSITORY (REPOSITORY_NAME, PRIMARY_NODE) VALUES ($repositoryName, $nodeUrl)")
      }
      db.update(sql"DELETE FROM REPOSITORY_NODE WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
      db.update(sql"INSERT INTO REPOSITORY_NODE (NODE_URL, REPOSITORY_NAME, STATUS) VALUES ($nodeUrl, $repositoryName, $RepositoryStatusEnabled)")
    }
  }

  def allRepositories()(implicit conn: Connection): Seq[Repository] = {
    defining(DB(conn)){ db =>
      val repos = db.select(sql"""SELECT REPOSITORY_NAME, PRIMARY_NODE FROM REPOSITORY"""){ rs =>
        (rs.getString("REPOSITORY_NAME"), rs.getString("PRIMARY_NODE"))
      }
      repos.map { case (repositoryName, primaryNode) =>
        val nodes = db.select(sql"""SELECT NODE_URL, STATUS FROM REPOSITORY_NODE WHERE REPOSITORY_NAME = $repositoryName"""){ rs =>
          RepositoryNode(rs.getString("NODE_URL"), rs.getString("STATUS"))
        }
        Repository(repositoryName, Option(primaryNode), nodes)
      }
    }
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit conn: Connection): Option[String] = {
    defining(DB(conn)){ db =>
      db.selectFirst(sql"""
        SELECT NODE_URL FROM REPOSITORY_NODE_STATUS
        WHERE NODE_URL NOT IN (
          SELECT NODE_URL FROM REPOSITORY_NODE WHERE REPOSITORY_NAME = $repositoryName AND STATUS = $RepositoryStatusEnabled
      )"""){ rs =>
        rs.getString("NODE_URL")
      }
    }
  }

  def promotePrimaryNode(nodeUrl: String, repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      db.update(sql"UPDATE REPOSITORY SET PRIMARY_NOE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName")
      db.update(sql"UPDATE REPOSITORY_NODE SET STATUS = $RepositoryStatusEnabled WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
    }
  }

}

case class Repository(name: String, primaryNode: Option[String], nodes: Seq[RepositoryNode]){
  lazy val enablesNodes  = nodes.filter(_.status == NodeManager.RepositoryStatusEnabled)
  lazy val disabledNodes = nodes.filter(_.status == NodeManager.RepositoryStatusDisabled)
}
case class RepositoryNode(nodeUrl: String, status: String)

case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[NodeStatusRepository]){
  lazy val enabledRepos  = repos.filter(_.status == NodeManager.RepositoryStatusEnabled)
  lazy val disabledRepos = repos.filter(_.status == NodeManager.RepositoryStatusDisabled)
}
case class NodeStatusRepository(repositoryName: String, status: String)
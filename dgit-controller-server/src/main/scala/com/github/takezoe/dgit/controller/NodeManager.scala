package com.github.takezoe.dgit.controller

import java.sql.Connection

import com.github.takezoe.resty.HttpClientSupport

import org.slf4j.LoggerFactory
import com.github.takezoe.scala.jdbc._
import syntax._

object Status {
  val Ready = "ready"
  val Preparing = "preparing"
}

// TODO Should be a class?
object NodeManager extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)

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
        sql"SELECT COUNT(*) AS COUNT FROM NODE WHERE NODE_URL = $nodeUrl"
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
              INSERT INTO NODE
                (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE)
              VALUES
                ($nodeUrl, ${System.currentTimeMillis}, $diskUsage)
            """)

      // TODO Fix this!!
      repos.foreach { repositoryName =>
        if(existRepository(repositoryName)){
          db.update(sql"""
                INSERT INTO NODE_REPOSITORY
                  (NODE_URL, REPOSITORY_NAME, STATUS)
                VALUES
                  ($nodeUrl, $repositoryName, ${Status.Preparing})
              """)
        }
      }
    }
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double)(implicit conn: Connection): Unit = {
    log.info(s"Update node status: $nodeUrl")
    defining(DB(conn)){ db =>
      db.update(sql"""
         UPDATE NODE SET
           LAST_UPDATE_TIME = ${System.currentTimeMillis},
           DISK_USAGE       = $diskUsage
         WHERE NODE_URL = $nodeUrl
      """)
    }
  }

  def removeNode(nodeUrl: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      // Update primary repository
      val repos = db.select(sql"SELECT REPOSITORY_NAME FROM REPOSITORY WHERE PRIMARY_NODE = $nodeUrl "){ rs =>
        rs.getString("REPOSITORY_NAME")
      }

      repos.foreach { repositoryName =>
        val nextPrimaryNodeUrl = db.selectFirst[String](sql"""
          SELECT NODE_URL
          FROM NODE_REPOSITORY
          WHERE NODE_URL <> $nodeUrl AND REPOSITORY_NAME = $repositoryName AND STATUS = ${Status.Ready}
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
      db.update(sql"DELETE FROM NODE_REPOSITORY")
      db.update(sql"DELETE FROM NODE")
    }
  }

  def allNodes()(implicit conn: Connection): Seq[(String, NodeStatus)] = {
    defining(DB(conn)){ db =>
      db.select(sql"SELECT NODE_URL, LAST_UPDATE_TIME, DISK_USAGE FROM NODE"){ rs =>
        val nodeUrl   = rs.getString("NODE_URL")
        val timestamp = rs.getLong("LAST_UPDATE_TIME")
        val diskUsage = rs.getDouble("DISK_USAGE")
        val repos     = db.select(
          sql"SELECT REPOSITORY_NAME, STATUS FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl"
        ){ rs => NodeStatusRepository(rs.getString("REPOSITORY_NAME"), rs.getString("STATUS")) }

        (nodeUrl, NodeStatus(timestamp, diskUsage, repos))
      }
    }
  }

  def getNodeStatus(nodeUrl: String)(implicit conn: Connection): Option[NodeStatus] = {
    defining(DB(conn)){ db =>
      db.selectFirst(sql"SELECT NODE_URL, LAST_UPDATED_TIME, DISK_USAGE FROM NODE WHERE NODE_URL = $nodeUrl"){ rs =>
        // TODO Avoid N + 1 queries...
        val repos = db.select(sql"SELECT REPOSITORY_NAME, STATUS FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl"){ rs =>
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
      db.select(sql"SELECT NODE_URL FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
        rs.getString("NODE_URL")
      }
    }
  }

  def deleteRepository(repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      db.update(sql"DELETE FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName")
      db.update(sql"DELETE FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName")
    }
  }

  def createRepository(nodeUrl: String, repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      if(!existRepository(repositoryName)){
        db.update(sql"INSERT INTO REPOSITORY (REPOSITORY_NAME, PRIMARY_NODE, LAST_UPDATE_TIME) VALUES ($repositoryName, $nodeUrl, ${System.currentTimeMillis})")
      }
      db.update(sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
      db.update(sql"INSERT INTO NODE_REPOSITORY (NODE_URL, REPOSITORY_NAME, STATUS) VALUES ($nodeUrl, $repositoryName, ${Status.Ready})")
    }
  }

  def allRepositories()(implicit conn: Connection): Seq[Repository] = {
    defining(DB(conn)){ db =>
      val repos = db.select(sql"""SELECT REPOSITORY_NAME, PRIMARY_NODE FROM REPOSITORY"""){ rs =>
        (rs.getString("REPOSITORY_NAME"), rs.getString("PRIMARY_NODE"))
      }
      repos.map { case (repositoryName, primaryNode) =>
        val nodes = db.select(sql"""SELECT NODE_URL, STATUS FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"""){ rs =>
          RepositoryNode(rs.getString("NODE_URL"), rs.getString("STATUS"))
        }
        Repository(repositoryName, Option(primaryNode), nodes)
      }
    }
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit conn: Connection): Option[String] = {
    defining(DB(conn)){ db =>
      db.selectFirst(sql"""
        SELECT NODE_URL FROM NODE
        WHERE NODE_URL NOT IN (
          SELECT NODE_URL FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName AND STATUS = ${Status.Ready}
      )"""){ rs =>
        rs.getString("NODE_URL")
      }
    }
  }

  def promotePrimaryNode(nodeUrl: String, repositoryName: String)(implicit conn: Connection): Unit = {
    defining(DB(conn)){ db =>
      db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName")
      db.update(sql"UPDATE NODE_REPOSITORY SET STATUS = ${Status.Ready} WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
    }
  }

}

case class Repository(name: String, primaryNode: Option[String], nodes: Seq[RepositoryNode]){
  lazy val readyNodes  = nodes.filter(_.status == Status.Ready)
  lazy val preparingNodes = nodes.filter(_.status == Status.Preparing)
}
case class RepositoryNode(nodeUrl: String, status: String)

case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[NodeStatusRepository]){
  lazy val readyRepos  = repos.filter(_.status == Status.Ready)
  lazy val preparingRepos = repos.filter(_.status == Status.Preparing)
}
case class NodeStatusRepository(repositoryName: String, status: String)
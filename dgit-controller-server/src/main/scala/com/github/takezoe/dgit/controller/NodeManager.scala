package com.github.takezoe.dgit.controller

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.takezoe.resty.HttpClientSupport
import org.slf4j.LoggerFactory
import com.github.takezoe.scala.jdbc._

object Status {
  val Ready = "ready"
  val Preparing = "preparing"
}

// TODO Should be a class?
object NodeManager extends HttpClientSupport {

  private val log = LoggerFactory.getLogger(getClass)

  def existNode(nodeUrl: String): Boolean = Database.withDB { db =>
    val count = db.selectFirst(sql"SELECT COUNT(*) AS COUNT FROM NODE WHERE NODE_URL = $nodeUrl")(_.getInt("COUNT"))

    count match {
      case Some(i) if i > 0 => true
      case _ => false
    }
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[JoinNodeRepository], replica: Int): Unit = Database.withDB { db =>
    log.info(s"Add new node: $nodeUrl")

    db.transaction {
      val timestamp = System.currentTimeMillis
      db.update(sql"INSERT INTO NODE (NODE_URL, LAST_UPDATE_TIME, DISK_USAGE) VALUES ($nodeUrl, $timestamp, $diskUsage)")
    }

    repos.foreach { repo =>
      RepositoryLock.execute(repo.name){
        db.transaction {
          val repository = getRepositoryStatus(repo.name)

          repository match  {
            case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < replica =>
              if(x.primaryNode.isEmpty){
                db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = ${repo.name}")
              }
              db.update(sql"INSERT INTO NODE_REPOSITORY (NODE_URL, REPOSITORY_NAME, STATUS) VALUES ($nodeUrl, ${repo.name}, ${Status.Ready})")
            case _ =>
              try {
                httpDelete(s"$nodeUrl/api/repos/${repo.name}")
              } catch {
                case e: Exception => log.error(s"Failed to delete repository ${repo.name} from $nodeUrl", e)
              }
          }
        }
      }
    }
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): Unit = Database.withDB { db =>
    log.info(s"Update node status: $nodeUrl")

    db.transaction {
      val timestamp = System.currentTimeMillis
      db.update(sql"UPDATE NODE SET LAST_UPDATE_TIME = $timestamp, DISK_USAGE = $diskUsage WHERE NODE_URL = $nodeUrl")
    }
  }

  def removeNode(nodeUrl: String): Unit = Database.withDB { db =>
    val repos = db.select(sql"SELECT REPOSITORY_NAME FROM REPOSITORY WHERE PRIMARY_NODE = $nodeUrl ") { rs =>
      rs.getString("REPOSITORY_NAME")
    }

    repos.foreach { repositoryName =>
      RepositoryLock.execute(repositoryName){
        db.transaction {
          val nextPrimaryNodeUrl = db.selectFirst[String](sql"""
            SELECT NODE_URL FROM NODE_REPOSITORY
            WHERE NODE_URL <> $nodeUrl AND REPOSITORY_NAME = $repositoryName AND STATUS = ${Status.Ready}
          """)(_.getString("NODE_URL"))

          nextPrimaryNodeUrl match {
            case Some(nodeUrl) =>
              db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName")
            case None =>
              db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE REPOSITORY_NAME = $repositoryName")
              log.error(s"All nodes for $repositoryName has been retired.")
          }

          db.update(sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
        }
      }
    }

    db.transaction {
      db.update(sql"DELETE FROM NODE")
    }
  }

  def allNodes(): Seq[(String, NodeStatus)] = Database.withDB { db =>
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

  /**
   * NOTE: This method must be used in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): Unit = Database.withDB { db =>
    db.transaction {
      db.update(sql"UPDATE REPOSITORY SET LAST_UPDATE_TIME = $timestamp WHERE REPOSITORY_NAME = $repositoryName")
    }
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): Unit = Database.withDB { db =>
    db.transaction {
      db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL WHERE PRIMARY_NODE = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
      db.update(sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
    }
  }


  def deleteRepository(repositoryName: String): Unit = Database.withDB { db =>
    db.transaction {
      db.update(sql"DELETE FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName")
    }
  }

  def insertRepository(repositoryName: String): Long = Database.withDB { db =>
    db.transaction {
      val timestamp = System.currentTimeMillis
      db.update(sql"INSERT INTO REPOSITORY (REPOSITORY_NAME, PRIMARY_NODE, LAST_UPDATE_TIME) VALUES ($repositoryName, NULL, $timestamp)")
    }
  }

  def insertNodeRepository(nodeUrl: String, repositoryName: String): Unit = Database.withDB { db =>
    db.transaction {
      if(getRepositoryStatus(repositoryName).map(_.primaryNode.isEmpty).getOrElse(false)){
        db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = $nodeUrl WHERE REPOSITORY_NAME = $repositoryName")
      }
      db.update(sql"DELETE FROM NODE_REPOSITORY WHERE NODE_URL = $nodeUrl AND REPOSITORY_NAME = $repositoryName")
      db.update(sql"INSERT INTO NODE_REPOSITORY (NODE_URL, REPOSITORY_NAME, STATUS) VALUES ($nodeUrl, $repositoryName, ${Status.Ready})")
    }
  }

  def getRepositoryStatus(repositoryName: String): Option[Repository] = Database.withDB { db =>
    val repos = db.selectFirst(sql"SELECT REPOSITORY_NAME, PRIMARY_NODE, LAST_UPDATE_TIME FROM REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
      (rs.getString("REPOSITORY_NAME"), rs.getString("PRIMARY_NODE"), rs.getLong("LAST_UPDATE_TIME"))
    }
    repos.map { case (repositoryName, primaryNode, timestamp) =>
      val nodes = db.select(sql"SELECT NODE_URL, STATUS FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
        RepositoryNode(rs.getString("NODE_URL"), rs.getString("STATUS"))
      }
      Repository(repositoryName, Option(primaryNode), timestamp, nodes)
    }
  }

  def allRepositories(): Seq[Repository] = Database.withDB { db =>
    val repos = db.select(sql"SELECT REPOSITORY_NAME, PRIMARY_NODE, LAST_UPDATE_TIME FROM REPOSITORY"){ rs =>
      (rs.getString("REPOSITORY_NAME"), rs.getString("PRIMARY_NODE"), rs.getLong("LAST_UPDATE_TIME"))
    }
    repos.map { case (repositoryName, primaryNode, timestamp) =>
      val nodes = db.select(sql"SELECT NODE_URL, STATUS FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName"){ rs =>
        RepositoryNode(rs.getString("NODE_URL"), rs.getString("STATUS"))
      }
      Repository(repositoryName, Option(primaryNode), timestamp, nodes)
    }
  }

  def getUrlOfAvailableNode(repositoryName: String): Option[String] = Database.withDB { db =>
    db.selectFirst(sql"""
      SELECT NODE_URL FROM NODE WHERE NODE_URL NOT IN (
        SELECT NODE_URL FROM NODE_REPOSITORY WHERE REPOSITORY_NAME = $repositoryName AND STATUS = ${Status.Ready}
      )
    """){ rs => rs.getString("NODE_URL") }
  }

}

case class Repository(name: String, primaryNode: Option[String], timestamp: Long, nodes: Seq[RepositoryNode]){
  @JsonIgnore
  lazy val readyNodes  = nodes.filter(_.status == Status.Ready)
  @JsonIgnore
  lazy val preparingNodes = nodes.filter(_.status == Status.Preparing)
}
case class RepositoryNode(nodeUrl: String, status: String)

case class NodeStatus(timestamp: Long, diskUsage: Double, repos: Seq[NodeStatusRepository]){
  @JsonIgnore
  lazy val readyRepos  = repos.filter(_.status == Status.Ready)
  @JsonIgnore
  lazy val preparingRepos = repos.filter(_.status == Status.Preparing)
}
case class NodeStatusRepository(repositoryName: String, status: String)
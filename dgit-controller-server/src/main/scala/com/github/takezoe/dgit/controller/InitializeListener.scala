package com.github.takezoe.dgit.controller

import java.sql.Connection
import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._
import akka.event.Logging
import io.github.gitbucket.solidbase.Solidbase

import scala.concurrent.Await
import scala.concurrent.duration._
import io.github.gitbucket.solidbase.migration.LiquibaseMigration
import io.github.gitbucket.solidbase.model.{Module, Version}
import liquibase.database.core.PostgresDatabase
import com.github.takezoe.scala.jdbc._
import syntax._

@WebListener
class InitializeListener extends ServletContextListener {

  private val system = ActorSystem("mySystem")

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)

    Database.closeDataSource()
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()

    // Initialize the node status db
    Database.initializeDataSource(config.database)

    Database.withTransaction { implicit conn =>
      // Drop all tables
      defining(DB(conn)){ db =>
        db.update(sql"DROP TABLE IF EXISTS VERSIONS")
        db.update(sql"DROP TABLE IF EXISTS REPOSITORY_NODE")
        db.update(sql"DROP TABLE IF EXISTS REPOSITORY")
        db.update(sql"DROP TABLE IF EXISTS REPOSITORY_NODE_STATUS")

        if(checkTableExist()){
          db.update(sql"UPDATE REPOSITORY SET PRIMARY_NODE = NULL")
          db.update(sql"DELETE FROM NODE_REPOSITORY")
          db.update(sql"DELETE FROM NODE")
        }
      }
      // Re-create empty tables
      new Solidbase().migrate(conn, Thread.currentThread.getContextClassLoader, new PostgresDatabase(), DGitMigrationModule)
    }

    // Setup controllers
    Resty.register(new APIController(config))

    // Start background jobs
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config)), "tick")
  }

  protected def checkTableExist()(implicit conn: Connection): Boolean = {
    using(conn.getMetaData().getTables(null, null, "%", Array[String]("TABLE"))){ rs =>
      while(rs.next()){
        val tableName = rs.getString("TABLE_NAME")
        if(tableName.toUpperCase() == "VERSIONS"){
          return true
        }
      }
    }
    return false
  }

}

object DGitMigrationModule extends Module("dgit",
  new Version("1.0.0", new LiquibaseMigration("update/dgit-database-1.0.0.xml"))
)

class CheckRepositoryNodeActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive = {
    case _ => {
      // Check dead nodes
      val timeout = System.currentTimeMillis() - (60 * 1000)

      Database.withTransaction { implicit conn =>
        NodeManager.allNodes().foreach { case (nodeUrl, status) =>
          if(status.timestamp < timeout){
            log.warning(s"$nodeUrl is retired.")
            NodeManager.removeNode(nodeUrl)
          }
        }
      }

      val repos = Database.withTransaction { implicit conn =>
        NodeManager.allRepositories()
      }

      repos.filter { x => x.nodes.size < config.replica }.foreach { x =>
        x.primaryNode.foreach { primaryNode =>
          createReplicas(primaryNode, x.name, x.nodes.size)
        }
      }
    }
  }

  private def createReplicas(primaryNode: String, repositoryName: String, enabledNodes: Int): Unit = {
    val lackOfReplicas = config.replica - enabledNodes

    // TODO Need another solution to lock repository operation
    RepositoryLock.execute(repositoryName){
      (1 to lackOfReplicas).foreach { _ =>
        // TODO check disk usage as well
        Database.withTransaction { implicit conn =>
          NodeManager.getUrlOfAvailableNode(repositoryName).map { nodeUrl =>
            log.info(s"Create replica of ${repositoryName} at $nodeUrl")
            // Create replica repository
            httpPutJson(s"$nodeUrl/api/repos/${repositoryName}", CloneRequest(primaryNode))
            // update node status in the database
            NodeManager.createRepository(nodeUrl, repositoryName)
          }
        }
      }
    }
  }

}

case class CloneRequest(nodeUrl: String)

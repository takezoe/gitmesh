package com.github.takezoe.dgit.controller

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._
import akka.event.Logging
import io.github.gitbucket.solidbase.Solidbase

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import io.github.gitbucket.solidbase.migration.{LiquibaseMigration, SqlMigration}
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

    Database.withTransaction { conn =>
      // Drop all tables
      defining(DB(conn)){ db =>
        db.update(sql"DROP TABLE VERSIONS")
        db.update(sql"DROP TABLE REPOSITORY_NODE")
        db.update(sql"DROP TABLE REPOSITORY")
        db.update(sql"DROP TABLE REPOSITORY_NODE_STATUS")
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

}

object DGitMigrationModule extends Module("dgit",
  new Version("1.0.0", new LiquibaseMigration("update/dgit-database-1.0.0.xml"))
)

class CheckRepositoryNodeActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive = {
    case _ => {
      // Check died nodes
      val timeout = System.currentTimeMillis() - (60 * 1000)

      NodeManager.allNodes().foreach { case (nodeUrl, status) =>
        if(status.timestamp < timeout){
          log.warning(s"$nodeUrl is retired.")
          NodeManager.removeNode(nodeUrl)
        }
      }

      // Add replica if it's needed
      NodeManager.allRepositories()
        .filter { repository => repository.nodes.size < config.replica }
        .foreach { repository =>
          val lackOfReplicas = config.replica - repository.nodes.size
          if(lackOfReplicas > 0){
            RepositoryLock.execute(repository.name){
              (1 to config.replica - repository.nodes.size).flatMap { _ =>
                NodeManager.getUrlOfAvailableNode(repository.name).map { nodeUrl =>
                  // Create replica
                  httpPutJson(s"$nodeUrl/api/repos/${repository.name}", CloneRequest(repository.primaryNode))
                  // Update node status
                  NodeManager.getNodeStatus(nodeUrl).foreach { status =>
                    NodeManager.updateNodeStatus(nodeUrl, status.diskUsage, status.repos :+ repository.name)
                  }
                }
              }
            }
          }
        }
    }
  }
}

case class CloneRequest(nodeUrl: String)

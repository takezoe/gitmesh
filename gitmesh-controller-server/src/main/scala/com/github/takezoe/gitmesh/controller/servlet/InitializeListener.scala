package com.github.takezoe.gitmesh.controller.servlet

import java.sql.Connection
import javax.servlet.{ServletContextEvent, ServletContextListener}

import akka.actor._
import com.github.takezoe.gitmesh.controller.data.{DataStore, Database}
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.job.CheckRepositoryNodeActor
import com.github.takezoe.gitmesh.controller.util.{Config, ControllerLock, Migration}
import com.github.takezoe.gitmesh.controller.util.syntax._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core._

import scala.concurrent.Await
import scala.concurrent.duration._

//@WebListener
class InitializeListener extends ServletContextListener {

  private val system = ActorSystem("mySystem")

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)

    Database.closeDataSource()
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    implicit val config = Config.load()
    GitRepositoryProxyServer.initialize(config)

    val dataStore = new DataStore()

    // Initialize the node status db
    Database.initializeDataSource(config.database)

    Database.withConnection { conn =>
      if (checkTableExist(conn)) {
        // Clear cluster status
        if(ControllerLock.runForMaster("**master**", config.url, config.deadDetectionPeriod.master)) {
          Database.withTransaction(conn){
            Repositories.update(_.primaryNode asNull).execute(conn)
            NodeRepositories.delete().execute(conn)
            Nodes.delete().execute(conn)
            ExclusiveLocks.delete().execute(conn)
          }
        }
      }

      // Re-create empty tables
      new Solidbase().migrate(
        conn,
        Thread.currentThread.getContextClassLoader,
        liquibaseDriver(config.database.url),
        Migration
      )
      conn.commit()
    }

    // Setup controllers
    //Resty.register(new APIController(dataStore))

    // Start background jobs
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("checkNodes", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config, dataStore)), "tick")
  }

  private def liquibaseDriver(url: String): liquibase.database.Database = {
    if(url.startsWith("jdbc:postgresql://")){
      new PostgresDatabase()
    } else if(url.startsWith("jdbc:mysql://")){
      new MySQLDatabase()
    } else {
      new UnsupportedDatabase()
    }
  }

  protected def checkTableExist(conn: Connection): Boolean = {
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

//object GitMeshMigrationModule extends Module("gitmesh",
//  new Version("1.0.0", new LiquibaseMigration("update/gitmesh-database-1.0.0.xml"))
//)

//class CheckRepositoryNodeActor(implicit val config: Config, dataStore: DataStore) extends Actor with HttpClientSupport {
//
//  private val log = Logging(context.system, this)
//  //implicit override val httpClientConfig = config.httpClient
//
//  override def receive = {
//    case _ => {
//      if(ControllerLock.runForMaster("**master**", config.url, config.deadDetectionPeriod.master)){
//        // Check dead nodes
//        val timeout = System.currentTimeMillis() - config.deadDetectionPeriod.node
//
//        dataStore.allNodes().foreach { node =>
//          if(node.timestamp < timeout){
//            log.warning(s"${node.url} is retired.")
//            dataStore.removeNode(node.url)
//          }
//        }
//
//        // Create replica
//        val repos = dataStore.allRepositories()
//
//        repos.filter { x => x.nodes.size < config.replica }.foreach { x =>
//          x.primaryNode.foreach { primaryNode =>
//            createReplicas(primaryNode, x.name, x.timestamp, x.nodes.size)
//          }
//        }
//      }
//    }
//  }
//
//  private def createReplicas(primaryNode: String, repositoryName: String, timestamp: Long, enabledNodes: Int): Unit = {
//    val lackOfReplicas = config.replica - enabledNodes
//
//    (1 to lackOfReplicas).foreach { _ =>
//      dataStore.getUrlOfAvailableNode(repositoryName).map { nodeUrl =>
//        log.info(s"Create replica of ${repositoryName} at $nodeUrl")
//
//        if(timestamp == InitialRepositoryId){
//          log.info("Create empty repository")
//          // Repository is empty
//          RepositoryLock.execute(repositoryName, "create replica") {  // TODO need shared lock?
//            httpPutJson(
//              s"$nodeUrl/api/repos/${repositoryName}/_clone",
//              CloneRequest(primaryNode, true),
//              builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
//            )
//            // Insert a node record here because cloning an empty repository is proceeded as 1-phase.
//            dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
//          }
//        } else {
//          log.info("Clone repository")
//          // Repository is not empty.
//          httpPutJson(
//            s"$nodeUrl/api/repos/${repositoryName}/_clone",
//            CloneRequest(primaryNode, false),
//            builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
//          )
//          // Insert a node record as PREPARING status here, updated to READY later
//          dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Preparing)
//        }
//      }
//    }
//  }
//
//}
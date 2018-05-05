package com.github.takezoe.gitmesh.controller.servlet

import java.sql.Connection
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}

import com.github.takezoe.gitmesh.controller.api.Routes
import com.github.takezoe.gitmesh.controller.data.{DataStore, Database}
import com.github.takezoe.gitmesh.controller.data.models.{ExclusiveLocks, NodeRepositories, Nodes, Repositories}
import com.github.takezoe.gitmesh.controller.util.{Config, ControllerLock, Migration}
import com.github.takezoe.gitmesh.controller.util.syntax.using
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core.{MySQLDatabase, PostgresDatabase, UnsupportedDatabase}
import org.http4s.servlet.syntax.ServletContextSyntax

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {

  override def contextInitialized(sce: ServletContextEvent) = {
    val context = sce.getServletContext
    val dataSource = new DataStore()
    val config = Config.load()
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

    context.mountService("helloService", Routes.service(dataSource))

//    // Start background jobs
//    val scheduler = QuartzSchedulerExtension(system)
//    scheduler.schedule("checkNodes", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config, dataStore)), "tick")
  }

  override def contextDestroyed(sce: ServletContextEvent) = {
    Database.closeDataSource()
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
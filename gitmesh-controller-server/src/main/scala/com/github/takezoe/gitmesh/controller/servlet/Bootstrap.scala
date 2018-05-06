package com.github.takezoe.gitmesh.controller.servlet

import java.sql.Connection
import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}

import akka.actor.{ActorSystem, Props}
import cats.effect.IO
import com.github.takezoe.gitmesh.controller.api.{Routes, Services}
import com.github.takezoe.gitmesh.controller.data._
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.job.CheckRepositoryNodeActor
import com.github.takezoe.gitmesh.controller.util._
import com.github.takezoe.gitmesh.controller.util.syntax.using
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core.{MySQLDatabase, PostgresDatabase, UnsupportedDatabase}
import org.http4s.client.blaze.Http1Client
import org.http4s.servlet.syntax.ServletContextSyntax
import org.http4s.server.middleware.CORS

import scala.concurrent.Await
import scala.concurrent.duration._

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {

  private val system = ActorSystem("mySystem")
  private val httpClient = Http1Client[IO]().unsafeRunSync

  override def contextInitialized(sce: ServletContextEvent) = {
    val context = sce.getServletContext
    implicit val config = Config.load()

    val dataStore = new DataStore()
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

    val services = new Services(dataStore, httpClient)

    context.mountService("helloService", CORS(Routes(services)))

    // Start background jobs
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("checkNodes", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config, dataStore)), "tick")
  }

  override def contextDestroyed(sce: ServletContextEvent) = {
    val f = system.terminate()
    Await.result(f, 30.seconds)

    httpClient.shutdown.unsafeRunSync()
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
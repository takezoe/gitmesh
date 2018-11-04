package com.github.takezoe.gitmesh.controller.servlet

import java.sql.{Connection, DriverManager}
import java.util.concurrent.TimeUnit

import javax.servlet.annotation.WebListener
import javax.servlet.{ServletContextEvent, ServletContextListener}
import cats.data.Kleisli
import cats.effect.IO
import com.github.takezoe.gitmesh.controller.api.{Routes, Services}
import com.github.takezoe.gitmesh.controller.data._
import com.github.takezoe.gitmesh.controller.job.CheckRepositoryNodeJob
import com.github.takezoe.gitmesh.controller.util._
import com.github.takezoe.gitmesh.controller.util.syntax.using
import fs2.Scheduler
import io.github.gitbucket.solidbase.Solidbase
import liquibase.database.core.{MySQLDatabase, PostgresDatabase, UnsupportedDatabase}
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import org.http4s.client.middleware.{Retry, RetryPolicy}
import org.http4s.servlet.syntax.ServletContextSyntax
import org.http4s.server.middleware.CORS

import scala.concurrent.duration._
import monix.execution.Cancelable

// TODO Use individual execution context instead of the global execution context!
import scala.concurrent.ExecutionContext.Implicits.global
import monix.execution.Scheduler.{global => monixScheduler}

@WebListener
class Bootstrap extends ServletContextListener with ServletContextSyntax {

  private implicit val config = Config.load()
  private implicit val scheduler = Scheduler.allocate[IO](4).unsafeRunSync()._1
  private var monix: Cancelable = null

  private val httpClient = Retry[IO](RetryPolicy { i =>
    if(i > config.httpClient.maxRetry) None else Some(config.httpClient.retryInterval.milliseconds)
  })(Http1Client[IO](BlazeClientConfig.defaultConfig.copy(
    requestTimeout      = config.httpClient.requestTimeout.milliseconds,
    idleTimeout         = config.httpClient.requestTimeout.milliseconds,
    maxTotalConnections = config.httpClient.maxConnections,
    maxWaitQueueLimit   = config.httpClient.maxWaitQueue
  )).unsafeRunSync)

  override def contextInitialized(sce: ServletContextEvent) = {
    val context = sce.getServletContext
    GitRepositoryProxyServer.initialize(config)

    val dataStore = new DataStore()

    val conn = DriverManager.getConnection(config.database.url, config.database.user, config.database.password)
    try {
      conn.setAutoCommit(false)

      if(checkTableExist(conn)){
        if(ControllerLock.runForMaster("**master**", config.url, config.deadDetectionPeriod.master)){
          dataStore.clearClusterStatus()
        }
      }

      new Solidbase().migrate(
        conn,
        Thread.currentThread.getContextClassLoader,
        liquibaseDriver(config.database.url),
        Migration
      )

      conn.commit()

    } finally {
      conn.close()
    }

    context.mountService("gitmeshControllerService", CORS(Routes(new Services(dataStore, httpClient))))
    monix = monixScheduler.scheduleWithFixedDelay(0, 30, TimeUnit.SECONDS, new CheckRepositoryNodeJob()(config, dataStore, httpClient))
  }

  override def contextDestroyed(sce: ServletContextEvent) = {
    monix.cancel()
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
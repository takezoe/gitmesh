package com.github.takezoe.gitmesh.controller.data

import java.sql.Connection

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.util.Config
import com.github.takezoe.gitmesh.controller.util.syntax._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import doobie.util.transactor.Transactor

object Database {

  private var dataSource: HikariDataSource = null
  var xa: Transactor.Aux[IO, Unit] = null

  private def createDataSource(config: Config.DatabaseConfig): Transactor.Aux[IO, Unit] = {
    Transactor.fromDriverManager[IO](
      config.driver,
      config.url,
      config.user,
      config.password
    )

//    new HikariDataSource(new HikariConfig().unsafeTap { hikariConfig =>
//      hikariConfig.setDriverClassName(config.driver)
//      hikariConfig.setJdbcUrl(config.url)
//      hikariConfig.setUsername(config.user)
//      hikariConfig.setPassword(config.password)
//      hikariConfig.setAutoCommit(false)
//      config.connectionTimeout.foreach(hikariConfig.setConnectionTimeout)
//      config.idleTimeout.foreach(hikariConfig.setIdleTimeout)
//      config.maxLifetime.foreach(hikariConfig.setMaxLifetime)
//      config.minimumIdle.foreach(hikariConfig.setMinimumIdle)
//      config.maximumPoolSize.foreach(hikariConfig.setMaximumPoolSize)
//    })
  }

  def initializeDataSource(config: Config.DatabaseConfig): Unit = {
    //dataSource = createDataSource(config)
    xa = createDataSource(config)
  }

  def closeDataSource(): Unit = {
//    dataSource.close()
  }

//  def withTransaction[T](conn: Connection)(f: => T): T = {
//    conn.setAutoCommit(false)
//    try {
//      val result = f
//      conn.commit()
//      result
//    } catch {
//      case e: Exception =>
//        conn.rollback()
//        throw e
//    }
//  }
//
//  def withConnection[T](f: (Connection) => T): T = {
//    using(xa.connect((): Unit).unsafeRunSync()){ conn =>
//      try {
//        f(conn)
//      } finally ignoreException {
//        conn.close()
//      }
//    }
//  }

}

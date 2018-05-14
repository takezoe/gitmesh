package com.github.takezoe.gitmesh.controller.data

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.util.Config
import doobie.hikari._, doobie.hikari.implicits._

object Database {

//  private var dataSource: HikariDataSource = null
  var xa: HikariTransactor[IO] = null

//  private def createDataSource(config: Config.DatabaseConfig): HikariTransactor[IO] = {
//    HikariTransactor.newHikariTransactor[IO](
//      config.driver,
//      config.url,
//      config.user,
//      config.password
//    ).unsafeRunSync()
//
////    new HikariDataSource(new HikariConfig().unsafeTap { hikariConfig =>
////      hikariConfig.setDriverClassName(config.driver)
////      hikariConfig.setJdbcUrl(config.url)
////      hikariConfig.setUsername(config.user)
////      hikariConfig.setPassword(config.password)
////      hikariConfig.setAutoCommit(false)
////      config.connectionTimeout.foreach(hikariConfig.setConnectionTimeout)
////      config.idleTimeout.foreach(hikariConfig.setIdleTimeout)
////      config.maxLifetime.foreach(hikariConfig.setMaxLifetime)
////      config.minimumIdle.foreach(hikariConfig.setMinimumIdle)
////      config.maximumPoolSize.foreach(hikariConfig.setMaximumPoolSize)
////    })
//  }

  def initializeDataSource(config: Config.DatabaseConfig): Unit = {
    xa = (for {
      xa <- HikariTransactor.newHikariTransactor[IO](
        config.driver,
        config.url,
        config.user,
        config.password
      )
      _ <- xa.configure { ds =>
        IO {
          config.idleTimeout.foreach(ds.setIdleTimeout)
          config.connectionTimeout.foreach(ds.setConnectionTimeout)
          config.maxLifetime.foreach(ds.setMaxLifetime)
          config.maximumPoolSize.foreach(ds.setMaximumPoolSize)
        }
      }
    } yield xa).unsafeRunSync()
  }

  def closeDataSource(): Unit = {
    xa.shutdown.unsafeRunSync()
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

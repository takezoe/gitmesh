package com.github.takezoe.gitmesh.controller.data

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.util.Config
import doobie.hikari._, doobie.hikari.implicits._

object Database {

  var xa: HikariTransactor[IO] = null

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

}

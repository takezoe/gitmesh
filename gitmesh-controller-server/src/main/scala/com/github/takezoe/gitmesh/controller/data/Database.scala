package com.github.takezoe.gitmesh.controller.data

import com.github.takezoe.gitmesh.controller.util.Config.DatabaseConfig
import com.typesafe.config.ConfigFactory
import io.getquill._

object Database {

  var db: MysqlJdbcContext[CompositeNamingStrategy2[SnakeCase.type, UpperCase.type]] = null

  def initDataSource(databaseConfig: DatabaseConfig): Unit = {
    val config = ConfigFactory.parseString(
      s"""
        |jdbcUrl  = "${databaseConfig.jdbcUrl}"
        |username = "${databaseConfig.username}"
        |password = "${databaseConfig.password}"
        |""".stripMargin)

    db = new MysqlJdbcContext(NamingStrategy(SnakeCase, UpperCase), config)
  }

  def closeDataSource(): Unit = {
    db.close()
  }

}

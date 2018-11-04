package com.github.takezoe.gitmesh.controller.data

import io.getquill._

object Database {

  val db = new MysqlJdbcContext(NamingStrategy(SnakeCase, UpperCase), "gitmesh.database")

  def closeDataSource(): Unit = {
    db.close()
  }

}

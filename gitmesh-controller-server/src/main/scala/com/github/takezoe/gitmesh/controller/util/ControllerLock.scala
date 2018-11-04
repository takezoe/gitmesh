package com.github.takezoe.gitmesh.controller.util

//import cats.effect.IO
//import doobie._
//import doobie.implicits._
//import cats.implicits._

import com.github.takezoe.gitmesh.controller.data.Database
import com.github.takezoe.gitmesh.controller.data.models.ExclusiveLock
import org.slf4j.LoggerFactory

object ControllerLock {

  private val db = Database.db
  import db._

  private val log = LoggerFactory.getLogger(ControllerLock.getClass)

  def runForMaster(key: String, node: String, timeout: Long): Boolean = {
    val timestamp = System.currentTimeMillis

    db.transaction {
      val lock = db.run(quote { query[ExclusiveLock].filter(_.lockKey == lift(key)).take(1) }).headOption
      lock match {
        case Some(ExclusiveLock(_, comment, _)) if comment == node =>
          db.run(quote {
            query[ExclusiveLock].filter(t => t.lockKey == lift(key) && t.comment.contains(lift(node))).update(_.lockTime -> lift(timestamp))
          })
          true
        case Some(ExclusiveLock(_, _, lockTime)) if lockTime < System.currentTimeMillis - timeout =>
          val result = db.run(quote {
            query[ExclusiveLock].filter(t => t.lockKey == lift(key)).update(_.lockTime -> lift(timestamp), _.comment -> lift(Some(node): Option[String]))
          })
          if(result > 0){
            true
          } else {
            log.info("Failed to get a lock")
            false
          }
        case Some(_) =>
          false
        case None =>
          val result = db.run(quote {
            query[ExclusiveLock].insert(lift(ExclusiveLock(
              lockKey  = key,
              comment  = Some(node),
              lockTime = timestamp
            )))
          })
          if(result > 0){
            true
          } else {
            log.info("Failed to get a lock")
            false
          }
      }
    }
  }

}

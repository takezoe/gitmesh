package com.github.takezoe.dgit.controller

import com.github.takezoe.scala.jdbc._
import org.slf4j.LoggerFactory

object ControllerLock {

  private val log = LoggerFactory.getLogger(ControllerLock.getClass)

  def runForMaster(key: String, node: String, timeout: Long = 5 * 60 * 1000): Boolean = Database.withDB { db =>
    db.transaction {
      val timestamp = System.currentTimeMillis

      val lock = db.selectFirst(
        sql"SELECT COMMENT, LOCK_TIME FROM LOCK WHERE LOCK_KEY = $key"
      ){ rs => (rs.getString("COMMENT"), rs.getLong("LOCK_TIME")) }

      lock match {
        // Already be a master
        case Some((comment, _)) if comment == node =>
          db.update(sql"UPDATE LOCK SET LOCK_TIME = $timestamp WHERE LOCK_KEY = $key AND COMMENT = $node")
          true
        // Timeout
        case Some((_, lockTime)) if lockTime < System.currentTimeMillis - timeout =>
          log.info(s"Lock $key has been timeout")
          try {
            db.update(sql"UPDATE LOCK SET COMMENT = $node, LOCK_TIME = $timestamp WHERE LOCK_KEY = $key")
            true
          } catch {
            case e: Exception =>
              log.info("Failed to get a lock: " + e.toString)
              false
          }
        // Other controller node has a lock
        case Some(_) => false
        // Possible to get a lock
        case None => try {
          db.update(sql"INSERT INTO LOCK (LOCK_KEY, COMMENT, LOCK_TIME) VALUES ($key, $node, $timestamp)")
          true
        } catch {
          case e: Exception =>
            log.info("Failed to get a lock: " + e.toString)
            false
        }
      }
    }
  }

}

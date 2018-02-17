package com.github.takezoe.gitmesh.controller

import org.slf4j.LoggerFactory
import models._
import com.github.takezoe.tranquil._
import com.github.takezoe.tranquil.Dialect.mysql

object ControllerLock {

  private val log = LoggerFactory.getLogger(ControllerLock.getClass)

  def runForMaster(key: String, node: String, timeout: Long = 5 * 60 * 1000): Boolean = Database.withConnection { conn =>
    Database.withTransaction(conn){
      val timestamp = System.currentTimeMillis
      val lock = ExclusiveLocks.filter(_.lockKey eq key).map(t => t.comment ~ t.lockTime).firstOption(conn)

      lock match {
        // Already be a master
        case Some((Some(comment), _)) if comment == node =>
          ExclusiveLocks.update(_.lockTime -> timestamp).filter(t => (t.lockKey eq key) && (t.comment eq node)).execute(conn)
          true
        // Timeout
        case Some((_, lockTime)) if lockTime < System.currentTimeMillis - timeout =>
          log.info(s"Lock $key has been timeout")
          try {
            ExclusiveLocks.update(t => (t.comment -> node) ~ (t.lockTime -> timestamp)).filter(_.lockKey eq key).execute(conn)
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
          ExclusiveLocks.insert(ExclusiveLock(key, Some(node), timestamp)).execute(conn)
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

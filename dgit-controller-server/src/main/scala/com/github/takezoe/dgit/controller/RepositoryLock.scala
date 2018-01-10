package com.github.takezoe.dgit.controller

import com.github.takezoe.scala.jdbc._
import org.slf4j.LoggerFactory

/**
 * Offers the locking mechanism for a repository.
 *
 * This lock is made using the database and get an independent connection inside of this object.
 * When you configure the connection pool, you have to consider this connection.
 */
object RepositoryLock {

  private val log = LoggerFactory.getLogger(RepositoryLock.getClass)

  def execute[T](repositoryName: String, comment: String)(action: => T): T = Database.withDB { db =>
    _execute(db, repositoryName, comment, 0)(action)
  }

  private def _execute[T](db: DB, repositoryName: String, comment: String, retry: Int)(action: => T): T = {
    try {
      try {
        db.transaction {
          val lock = db.selectFirst(
            sql"SELECT COMMENT, LOCK_TIME AS COUNT FROM LOCK WHERE LOCK_KEY = $repositoryName"
          ){ rs => (rs.getString("COMMENT"), rs.getLong("LOCK_TIME")) }

          lock.foreach { case (comment, lockTime) =>
            throw new RepositoryLockException(s"$repositoryName is already locked since ${new java.util.Date(lockTime).toString}: $comment")
          }

          val timestamp = System.currentTimeMillis
          db.update(sql"INSERT INTO LOCK (LOCK_KEY, COMMENT, LOCK_TIME) VALUES ($repositoryName, $comment, $timestamp)")
        }

        try {
          action
        } catch {
          case e: Exception => throw new ActionException(e)
        }
      } finally {
        db.transaction {
          db.update(sql"DELETE FROM LOCK WHERE LOCK_KEY = $repositoryName")
        }
      }
    } catch {
      case e: ActionException => throw e.getCause
      case _: Exception if retry < 10 =>
        Thread.sleep(1000)
        log.info(s"Retry to get lock for $repositoryName")
        _execute(db, repositoryName, comment, retry + 1)(action)
    }
  }

  private class ActionException(e: Exception) extends RuntimeException(e)
  class RepositoryLockException(message: String) extends RuntimeException(message)

}

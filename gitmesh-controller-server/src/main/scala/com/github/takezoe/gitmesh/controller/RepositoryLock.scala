package com.github.takezoe.gitmesh.controller

import java.sql.Connection

import org.slf4j.LoggerFactory
import models._
import com.github.takezoe.tranquil._
import com.github.takezoe.tranquil.Dialect.mysql

/**
 * Offers the locking mechanism for a repository.
 *
 * This lock is made using the database and get an independent connection inside of this object.
 * When you configure the connection pool, you have to consider this connection.
 */
object RepositoryLock {

  private val log = LoggerFactory.getLogger(RepositoryLock.getClass)

  def execute[T](repositoryName: String, comment: String)(action: => T): T = Database.withConnection { conn =>
    _execute(conn, repositoryName, comment, 0)(action)
  }

  private def _execute[T](conn: Connection, repositoryName: String, comment: String, retry: Int)(action: => T): T = {
    try {
      try {
        Database.withTransaction(conn){
          val lock = ExclusiveLocks.filter(_.lockKey eq repositoryName).map(t => t.comment ~ t.lockTime).firstOption(conn)

          lock.foreach { case (comment, lockTime) =>
            throw new RepositoryLockException(
              s"$repositoryName is already locked since ${new java.util.Date(lockTime).toString}: ${comment.getOrElse("")}"
            )
          }

          ExclusiveLocks.insert(ExclusiveLock(repositoryName, if(comment.isEmpty) Some(comment) else None, System.currentTimeMillis))
        }

        try {
          action
        } catch {
          case e: Exception => throw new ActionException(e)
        }
      } finally {
        Database.withTransaction(conn){
          ExclusiveLocks.delete().filter(_.lockKey eq repositoryName).execute(conn)
        }
      }
    } catch {
      case e: ActionException => throw e.getCause
      case _: Exception if retry < 10 =>
        Thread.sleep(1000) // TODO should be configurable.
        log.info(s"Retry to get lock for $repositoryName")
        _execute(conn, repositoryName, comment, retry + 1)(action)
    }
  }

  private class ActionException(e: Exception) extends RuntimeException(e)
  class RepositoryLockException(message: String) extends RuntimeException(message)

}

package com.github.takezoe.gitmesh.controller.util

import com.github.takezoe.gitmesh.controller.data.Database
import com.github.takezoe.gitmesh.controller.data.models.ExclusiveLock
import org.slf4j.LoggerFactory

/**
 * Offers the locking mechanism for a repository.
 *
 * This lock is made using the database and get an independent connection inside of this object.
 * When you configure the connection pool, you have to consider this connection.
 */
object RepositoryLock {

  private val db = Database.db
  import db._

  private val log = LoggerFactory.getLogger(RepositoryLock.getClass)

  def lock(repositoryName: String, comment: String)(implicit config: Config): Either[Throwable, Unit] = {
    db.transaction {
      val lock = db.run(quote {
        query[ExclusiveLock].filter(_.lockKey == lift(repositoryName))
      }).headOption

      lock match {
        case Some(lock) =>
          val e = new RepositoryLockException(s"$repositoryName is already locked since ${new java.util.Date(lock.lockTime).toString}: ${lock.comment.getOrElse("")}")
          Left(e)
        case None =>
          db.run(quote {
            query[ExclusiveLock].insert(lift(ExclusiveLock(
              lockKey  = repositoryName,
              comment  = Some(comment),
              lockTime = System.currentTimeMillis()
            )))
          })
          Right((): Unit)
      }
    }
  }

  def unlock(repositoryName: String)(implicit config: Config): Unit = {
    ???
    db.transaction {
      db.run(quote { query[ExclusiveLock].filter(_.lockKey == lift(repositoryName)).delete })
    }
  }

  def execute[T](repositoryName: String, comment: String)(action: => T)(implicit config: Config): T = {
    _execute(repositoryName, comment, config.repositoryLock, 0)(action)
  }

  private def _execute[T](repositoryName: String, comment: String, retryConfig: Config.RepositoryLock, retry: Int)
                         (action: => T)(implicit config: Config): T = {
    try {
      try {
        lock(repositoryName, comment) match {
          case Right(_) => try {
            action
          } catch {
            case e: Exception => throw new ActionException(e)
          }
          case Left(e) => throw e
        }
      } finally {
        unlock(repositoryName)
      }
    } catch {
      case e: ActionException => throw e.getCause
      case _: Exception if retry < retryConfig.maxRetry =>
        Thread.sleep(retryConfig.retryInterval)
        log.info(s"Retry to get lock for $repositoryName")
        _execute(repositoryName, comment, retryConfig, retry + 1)(action)
    }
  }

  private class ActionException(e: Exception) extends RuntimeException(e)
  class RepositoryLockException(message: String) extends RuntimeException(message)

}

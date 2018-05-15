package com.github.takezoe.gitmesh.controller.util

import doobie._
import doobie.implicits._
import cats.effect.IO
import cats.implicits._
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

  private val log = LoggerFactory.getLogger(RepositoryLock.getClass)

  def lock(repositoryName: String, comment: String)(implicit config: Config): IO[Either[Throwable, Unit]] = {
    (for {
      lock <- sql"SELECT LOCK_KEY, COMMENT, LOCK_TIME FROM EXCLUSIVE_LOCK WHERE LOCK_KEY = $repositoryName".query[ExclusiveLock].option
      result <- lock match {
        case Some(lock) =>
          val e = new RepositoryLockException(s"$repositoryName is already locked since ${new java.util.Date(lock.lockTime).toString}: ${lock.comment.getOrElse("")}")
          val result: Either[Throwable, Unit] = Left(e)
          result.pure[ConnectionIO]
        case None =>
          sql"INSERT INTO EXCLUSIVE_LOCK (LOCK_KEY, COMMENT, LOCK_TIME) VALUES ($repositoryName, $comment, ${System.currentTimeMillis})".update.run.map { _ =>
            val result: Either[Throwable, Unit] = Right((): Unit)
            result
          }
      }
    } yield result).transact(Database.xa)
  }

  def unlock(repositoryName: String)(implicit config: Config): IO[Unit] = {
    (for {
      _ <- sql"DELETE FROM EXCLUSIVE_LOCK WHERE LOCK_KEY = $repositoryName".update.run
    } yield ()).transact(Database.xa)
  }

  def execute[T](repositoryName: String, comment: String)(action: => T)(implicit config: Config): T = {
    _execute(repositoryName, comment, config.repositoryLock, 0)(action)
  }

  private def _execute[T](repositoryName: String, comment: String, retryConfig: Config.RepositoryLock, retry: Int)
                         (action: => T)(implicit config: Config): T = {
    try {
      try {
        lock(repositoryName, comment).unsafeRunSync() match {
          case Right(_) => try {
            action
          } catch {
            case e: Exception => throw new ActionException(e)
          }
          case Left(e) => throw e
        }
      } finally {
        unlock(repositoryName).unsafeRunSync()
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

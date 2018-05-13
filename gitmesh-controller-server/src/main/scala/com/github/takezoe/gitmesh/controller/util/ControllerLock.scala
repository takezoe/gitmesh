package com.github.takezoe.gitmesh.controller.util

import cats.effect.IO
import doobie._
import doobie.implicits._
import cats.implicits._

import com.github.takezoe.gitmesh.controller.data.Database
import com.github.takezoe.gitmesh.controller.data.models.ExclusiveLock
import org.slf4j.LoggerFactory

object ControllerLock {

  private val log = LoggerFactory.getLogger(ControllerLock.getClass)

  def runForMaster(key: String, node: String, timeout: Long): IO[Boolean] = {
    val timestamp = System.currentTimeMillis
    (for {
      lock <- sql"SELECT LOCK_KEY, COMMENT, LOCK_TIME FROM EXCLUSIVE_LOCK WHERE LOCK_KEY = $key".query[ExclusiveLock].option
      result <- lock match {
        case Some(ExclusiveLock(_, comment, _)) if comment == node =>
          sql"UPDATE EXCLUSIVE_LOCK LOCK_TIME = $timestamp WHERE LOCK_KEY = $key AND COMMENT = $node".update.run.map { _ => true}
        case Some(ExclusiveLock(_, _, lockTime)) if lockTime < System.currentTimeMillis - timeout =>
          sql"UPDATE EXCLUSIVE_LOCK SET COMMENT = $node, LOCK_TIME = $timestamp WHERE LOCK_KEY = $key".update.run.attempt.map {
            case Right(_) =>
              true
            case Left(e) =>
              log.info("Failed to get a lock: " + e.toString)
              false
          }
        case Some(_) =>
          false.pure[ConnectionIO]
        case None =>
          sql"INSERT INTO EXCLUSIVE_LOCK (LOCK_KEY, COMMENT, LOCK_TIME) VALUES ($key, $node, $timestamp)".update.run.attempt.map {
            case Right(_) =>
              true
            case Left(e) =>
              log.info("Failed to get a lock: " + e.toString)
              false
          }
      }
    } yield result).transact(Database.xa)
  }

}

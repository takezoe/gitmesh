package com.github.takezoe.dgit.controller

import java.util.concurrent.ConcurrentHashMap

object RepositoryLock {

  private val locks = new ConcurrentHashMap[String, Unit]()

  def execute[T](repositoryName: String)(action: => T): T = {
    try {
      var result: T = null.asInstanceOf[T]
      locks.computeIfAbsent(repositoryName, _ => {
        result = action
        ()
      })
      result
    } finally {
      locks.remove(repositoryName)
    }
  }
}

package com.github.takezoe.dgit.repository

object Utils {

  def using[A <% { def close(): Unit }, B](resource: A)(f: A => B): B =
    try f(resource) finally {
      if(resource != null){
        ignoreException { resource.close() }
      }
    }

  def ignoreException[T](f: => T): Option[T] = {
    try {
      Some(f)
    } catch {
      case _: Exception => None
    }
  }

}

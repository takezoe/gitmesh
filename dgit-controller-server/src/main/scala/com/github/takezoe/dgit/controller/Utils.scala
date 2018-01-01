package com.github.takezoe.dgit.controller

object Utils {

  def using[A <% { def close(): Unit }, B](r: A)(f: A => B): B =
    try f(r) finally {
      if(r != null){
        ignoreException { r.close() }
      }
    }

  def using[A1 <% { def close(): Unit }, A2 <% { def close(): Unit }, B](r1: A1, r2: A2)(f: (A1, A2) => B): B =
    try f(r1, r2) finally {
      if(r1 != null){
        ignoreException { r1.close() }
      }
      if(r2 != null){
        ignoreException { r2.close() }
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

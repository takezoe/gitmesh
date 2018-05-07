package com.github.takezoe.gitmesh.controller.util

import cats.effect.IO
import io.circe.Decoder
import io.circe.jawn.CirceSupportParser
import org.http4s.{Request, Uri}
import org.http4s.util.CaseInsensitiveString

object syntax {

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

  def defining[T, R](value: T)(f: T => R): R = {
    f(value)
  }

  def ignoreException[T](f: => T): Option[T] = {
    try {
      Some(f)
    } catch {
      case _: Exception => None
    }
  }

  implicit class AnyOps[T](val value: T) extends AnyVal {
    def unsafeTap(f: T => Unit): T = {
      f(value)
      value
    }
  }

  def toUri(s: String): Uri = Uri.fromString(s).toTry.get

  implicit class RequestOps(val req: Request[IO]) extends AnyVal {
    def header(name: String): IO[String] = IO {
      req.headers.get(CaseInsensitiveString(name)).get.value
    }

    def decodeJson[T](implicit d: Decoder[T]): IO[T] = {
      req.as[String].flatMap { json =>
        IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[T])
      }
    }
  }

}

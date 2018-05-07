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

//  implicit class AnyOps[T](value: T){
//    def unsafeTap(f: T => Unit): T = {
//      f(value)
//      value
//    }
//  }

  def toUri(s: String): Uri = Uri.fromString(s).toTry.get

  def header(req: Request[IO], name: String): IO[String] = IO {
    req.headers.get(CaseInsensitiveString(name)).get.value
  }

  def decodeJson[T](req: Request[IO])(implicit d: Decoder[T]): IO[T] = {
    req.as[String].flatMap { json =>
      IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[T])
    }
  }

//  def writeFile(file: File, s: String): IO[Unit] = IO {
//    FileUtils.write(file, s, "UTF-8")
//  }
//
//  def deleteFile(file: File): IO[Unit] = IO {
//    if(file.exists){
//      FileUtils.forceDelete(file)
//    }
//  }
//
//  def deleteDir(dir: File): IO[Unit] = deleteFile(dir)
//
//  def logInfo(msg: String)(implicit logger: Logger): IO[Unit] = IO {
//    logger.info(msg)
//  }

}

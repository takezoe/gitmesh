package com.github.takezoe.gitmesh.repository.util

import java.io.File

import cats.effect.IO
import cats.implicits._
import io.circe.Decoder
import io.circe.jawn.CirceSupportParser
import org.apache.commons.io.FileUtils
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Request, Uri}
import org.slf4j.Logger

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

  def firstSuccess[T](seq: Seq[IO[T]]): IO[T] = {
    for {
      result <- seq.map(_.attempt).fold(IO.pure(Left(new NoSuchElementException()))){ case (current, next) =>
        current.flatMap {
          case x @ Right(_) => IO.pure(x)
          case _ => next
        }
      }
    } yield result match {
      case Right(x) => x
      case Left(e) => throw e
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

  implicit class FileOps(val file: File) extends AnyVal {
    def write(s: String): IO[Unit] = IO {
      FileUtils.write(file, s, "UTF-8")
    }

    def forceDelete(): IO[Unit] = IO {
      if(file.exists){
        FileUtils.forceDelete(file)
      }
    }
  }

  def logInfo(msg: String)(implicit logger: Logger): IO[Unit] = IO {
    logger.info(msg)
  }

}

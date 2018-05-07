package com.github.takezoe.gitmesh.repository.util

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.github.takezoe.gitmesh.repository.api.models.CloneRequest
import io.circe.Decoder
import io.circe.jawn.CirceSupportParser
import org.apache.commons.io.FileUtils
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Request, Uri}

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
    val result: IO[List[Either[Throwable, T]]] = seq.toList.map(_.attempt).sequence

    result.map { x =>
      x.view.takeWhile(_.isLeft)
    }.flatMap { x =>
      x.find(_.isRight) match {
        case Some(Right(x)) => IO.pure(x)
        case _              => x.reverse.find(_.isLeft) match {
          case Some(Left(x)) => throw x
          case _             => throw new NoSuchElementException()
        }
      }
    }
  }

  def toUri(s: String): Uri = Uri.fromString(s).toTry.get

  def header(req: Request[IO], name: String): IO[String] = IO {
    req.headers.get(CaseInsensitiveString(name)).get.value
  }

  def decodeJson[T](req: Request[IO])(implicit d: Decoder[T]): IO[T] = {
    req.as[String].flatMap { json =>
      IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[T])
    }
  }

  def writeFile(file: File, s: String): IO[Unit] = IO {
    FileUtils.write(file, s, "UTF-8")
  }

  def deleteFile(file: File): IO[Unit] = IO {
    if(file.exists){
      FileUtils.forceDelete(file)
    }
  }

  def deleteDir(dir: File): IO[Unit] = deleteFile(dir)

}

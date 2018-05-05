package com.github.takezoe.gitmesh.controller

import cats.effect.IO
import io.circe._
import org.http4s.{HttpService, _}
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
//import org.http4s._
//import org.http4s.circe._
//import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import io.circe.generic.auto._

object routes {

  def service(dataStore: DataStore) = HttpService[IO] {
    case GET -> Root / "hello" / name =>
      Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
    case GET -> Root / "api" / "nodes" =>
      Ok(Json.arr(dataStore.allNodes().map(_.asJson): _*))
    case GET -> Root / "api" / "repos" =>
      Ok(Json.arr(dataStore.allRepositories().map(_.asJson): _*))
  }


}

//import fs2.{Stream, StreamApp}
//// import fs2.{Stream, StreamApp}
//
//import fs2.StreamApp.ExitCode
//// import fs2.StreamApp.ExitCode
//
//import org.http4s.server.blaze._
//// import org.http4s.server.blaze._
//import scala.concurrent.ExecutionContext.Implicits.global
//
//object Main extends StreamApp[IO] {
//  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
//    BlazeBuilder[IO]
//      .bindHttp(8080, "localhost")
//      .mountService(routes.service, "/")
//      .serve
//}
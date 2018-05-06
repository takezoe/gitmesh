package com.github.takezoe.gitmesh.controller.api

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._

object Routes {

  def apply(services: Services) = HttpService[IO] {
    case req @ POST -> Root / "api" / "nodes" / "notify" =>
      services.joinNode(req)
    case GET -> Root / "api" / "nodes" =>
      services.listNodes()
    case GET -> Root / "api" / "repos" =>
      services.listRepositories()
    case POST -> Root / "api" / "repos" / repositoryName / "_delete" =>
      services.deleteRepository(repositoryName)
    case DELETE -> Root / "api" / "repos" / repositoryName =>
      services.deleteRepository(repositoryName)
    case POST -> Root / "api" / "repos" / repositoryName =>
      services.createRepository(repositoryName)
    case req @ POST -> Root / "api" / "repos"/ repositoryName / "_synced" =>
      services.repositorySynchronized(req, repositoryName)
    case POST -> Root / "api" / "repos"/ repositoryName / "_lock" =>
      services.lockRepository(repositoryName)
  }

}

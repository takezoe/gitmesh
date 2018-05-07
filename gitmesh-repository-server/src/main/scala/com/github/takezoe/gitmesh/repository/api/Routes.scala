package com.github.takezoe.gitmesh.repository.api

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.util.CaseInsensitiveString

object Routes {

  def apply(services: Services) = HttpService[IO] {
    case GET -> Root =>
      services.status()
    case req @ POST -> Root / "api" / "repos" / repositoryName =>
      services.createRepository(repositoryName, req)
    case GET -> Root / "api" / "repos" =>
      services.listRepositories()
    case GET -> Root / "api" / "repos" / repositoryName =>
      services.showRepositoryStatus(repositoryName)
    case DELETE -> Root / "api" / "repos" / repositoryName =>
      services.deleteRepository(repositoryName)
    case req @ PUT -> Root / "api" / "repos" / repositoryName / "_clone"=>
      services.cloneRepository(repositoryName, req)
    case req @ PUT -> Root / "api" / "repos"/ repositoryName / "_sync" =>
      services.synchronizeRepository(repositoryName, req)
  }

}

package com.github.takezoe.gitmesh.repository.api

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.github.takezoe.gitmesh.repository.api.models._
import com.github.takezoe.gitmesh.repository.util._
import com.github.takezoe.gitmesh.repository.util.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.client.dsl.io._

class Services(httpClient: Client[IO])(implicit val config: Config) extends GitOperations {

  implicit private val log = LoggerFactory.getLogger(classOf[Services])
  implicit private val decoderForCloneRequest = jsonOf[IO, CloneRequest]

  private def getRepositories(): Seq[String] = {
    val rootDir = new File(config.directory)
    rootDir.listFiles(_.isDirectory).toSeq.map(_.getName)
  }

  def status(): IO[Response[IO]] = {
    for {
      status <- IO {
        val rootDir = new File(config.directory)
        val diskUsage = rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble
        val repos = getRepositories()

        StatusResponse(
          url = config.url,
          diskUsage = diskUsage,
          repos = repos
        )
      }
      resp <- Ok(status.asJson)
    } yield resp
  }

  def createRepository(repositoryName: String, req: Request[IO]): IO[Response[IO]] = {
    for {
      _ <- logInfo(s"Create repository: $repositoryName")
      timestamp <- req.header("GITMESH-UPDATE-ID").map(_.toLong)
      // Delete the repository directory if it exists
      _ <- new File(config.directory, repositoryName).forceDelete()
      // git init
      _ <- gitInit(repositoryName)
      // Write timestamp
      _ <- new File(config.directory, s"$repositoryName.id").write(timestamp.toString)
      resp <- Ok()
    } yield resp
  }

  def listRepositories(): IO[Response[IO]] = {
    for {
      dirs <- IO {
        val rootDir = new File(config.directory)
        rootDir.listFiles(_.isDirectory).toSeq
      }
      repos <- dirs.map { dir =>
        gitCheckEmpty(dir.getName).map { empty => Repository(dir.getName, empty) }
      }.toList.sequence
      resp <- Ok(Json.arr(repos.map(_.asJson): _*))
    } yield resp
  }

  def showRepositoryStatus(repositoryName: String): IO[Response[IO]] = {
    for {
      exists <- IO { new File(config.directory, repositoryName).exists() }
      repo <- if(exists){
        gitCheckEmpty(repositoryName).map { empty =>
          Some(Repository(repositoryName, empty))
        }
      } else IO.pure(None)
      resp <- repo match {
        case Some(repo) => Ok(repo.asJson)
        case None => NotFound()
      }
    } yield resp
  }

  def deleteRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      _ <- logInfo(s"Delete repository: $repositoryName")
      _ <- new File(config.directory, repositoryName).forceDelete()
      _ <- new File(config.directory, s"$repositoryName.id").forceDelete()
      resp <- Ok()
    } yield resp
  }

  def cloneRepository(repositoryName: String, req: Request[IO]): IO[Response[IO]] = {
    for {
      request <- req.decodeJson[CloneRequest]
      timestamp <- req.header("GITMESH-UPDATE-ID").map(_.toLong)
      remoteUrl = s"${config.url}/git/$repositoryName.git"
      _ <- logInfo(s"Clone repository: $repositoryName from ${remoteUrl}")
      // Delete the repository directory if it exists
      _ <- new File(config.directory, repositoryName).forceDelete()
      _ <- if(request.empty){
        for {
          _ <- logInfo("Create empty repository")
          // Create an empty repository
          _ <- gitInit(repositoryName)
          // write timestamp
          result <- new File(config.directory, s"$repositoryName.id").write(timestamp.toString)
        } yield result
      } else {
        for {
          _ <- logInfo("Clone repository")
          // Clone the remote repository (without lock)
          _ <- gitClone(repositoryName, remoteUrl)
          // write timestamp
          _ <- new File(config.directory, s"$repositoryName.id").write(timestamp.toString)
          result <- httpClient.expect[String](POST(
            toUri(s"${request.nodeUrl}/api/repos/$repositoryName/_sync"),
            SynchronizeRequest(config.url).asJson,
            Header("GITMESH-UPDATE-ID", timestamp.toString)
          ))
        } yield result
      }
      resp <- Ok()
    } yield resp
  }

  def synchronizeRepository(repositoryName: String, req: Request[IO]): IO[Response[IO]] = {
    for {
      request <- req.decodeJson[SynchronizeRequest]
      remoteUrl = s"${request.nodeUrl}/git/$repositoryName.git"
      _ <- logInfo(s"Synchronize repository: $repositoryName with ${remoteUrl}")
      _ <- firstSuccess(config.controllerUrl.map { controllerUrl =>
        httpClient.expect[String](POST(toUri(s"${controllerUrl}/api/repos/$repositoryName/_lock")))
      })
      _ <- gitPushAll(repositoryName, remoteUrl)
      _ <- firstSuccess(config.controllerUrl.map { controllerUrl =>
        httpClient.expect[String](POST(
          toUri(s"${controllerUrl}/api/repos/$repositoryName/_synced"),
          SynchronizedRequest(request.nodeUrl).asJson
        ))
      })
      resp <- Ok()
    } yield resp
  }

}

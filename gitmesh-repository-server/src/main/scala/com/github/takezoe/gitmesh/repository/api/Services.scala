package com.github.takezoe.gitmesh.repository.api

import java.io.File

import cats.effect.IO
import cats.implicits._
import com.github.takezoe.gitmesh.repository.api.models._
import com.github.takezoe.gitmesh.repository.util._
import org.http4s.client.Client
import org.http4s.dsl.io._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.jawn.CirceSupportParser
import io.circe.syntax._
import org.apache.commons.io.FileUtils
import org.http4s.client.dsl.io._
import org.http4s.util.CaseInsensitiveString

class Services(httpClient: Client[IO])(implicit val config: Config) extends GitOperations {

  private val log = LoggerFactory.getLogger(classOf[Services])
  implicit val decoderForCloneRequest = jsonOf[IO, CloneRequest]

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
    log.info(s"Create repository: $repositoryName")
    for {
      timestamp <- IO {
        req.headers.get(CaseInsensitiveString("GITMESH-UPDATE-ID")).get.value.toLong
      }
      _ <- IO {
        // Delete the repository directory if it exists
        val dir = new File(config.directory, repositoryName)
        if(dir.exists){
          FileUtils.forceDelete(dir)
        }
      }
      _ <- gitInit(repositoryName)
      _ <- IO {
        // Write timestamp
        val file = new File(config.directory, s"$repositoryName.id")
        FileUtils.write(file, timestamp.toString, "UTF-8")
      }
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
        gitCheckEmpty(dir.getName).map { empty =>
          Repository(dir.getName, empty)
        }
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
    log.info(s"Delete repository: $repositoryName")
    for {
      _ <- IO {
        val dir = new File(config.directory, repositoryName)
        if(dir.exists){
          FileUtils.forceDelete(dir)
        }
        val file = new File(config.directory, s"$repositoryName.id")
        if(file.exists){
          FileUtils.forceDelete(file)
        }
      }
      resp <- Ok()
    } yield resp
  }

  def cloneRepository(repositoryName: String, req: Request[IO]): IO[Response[IO]] = {
    for {
      request <- req.as[String].flatMap { json =>
        IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[CloneRequest])
      }
      timestamp <- IO {
        req.headers.get(CaseInsensitiveString("GITMESH-UPDATE-ID")).get.value.toLong
      }
      remoteUrl = s"${config.url}/git/$repositoryName.git"
      _ <- IO {
        log.info(s"Clone repository: $repositoryName from ${remoteUrl}")
      }
      _ <- IO {
        // Delete the repository directory if it exists
        val dir = new File(config.directory, repositoryName)
        if(dir.exists){
          FileUtils.forceDelete(dir)
        }
      }
      _ <- if(request.empty){
        log.info("Create empty repository")
        for {
          // Create an empty repository
          _ <- gitInit(repositoryName)
          // write timestamp
          result <- IO {
            val file = new File(config.directory, s"$repositoryName.id")
            FileUtils.write(file, timestamp.toString, "UTF-8")
          }
        } yield result
      } else {
        log.info("Clone repository")
        for {
          // Clone the remote repository (without lock)
          _ <- gitClone(repositoryName, remoteUrl)
          // write timestamp
          _ <- IO {
            val file = new File(config.directory, s"$repositoryName.id")
            FileUtils.write(file, timestamp.toString, "UTF-8")
          }
          result <- httpClient.expect[String](POST(
            Uri.fromString(s"${request.nodeUrl}/api/repos/$repositoryName/_sync").toTry.get,
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
      request <- req.as[String].flatMap { json =>
        IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[SynchronizeRequest])
      }
      timestamp <- IO {
        req.headers.get(CaseInsensitiveString("GITMESH-UPDATE-ID")).get.value.toLong
      }
      remoteUrl = s"${request.nodeUrl}/git/$repositoryName.git"
      _ <- IO {
        log.info(s"Synchronize repository: $repositoryName with ${remoteUrl}")
      }
      // TODO retry request to other controllers
      _ <- httpClient.expect[String](POST(
        Uri.fromString(s"${config.controllerUrl.head}/api/repos/$repositoryName/_lock").toTry.get
      ))
      _ <- gitPushAll(repositoryName, remoteUrl)
      // TODO retry request to other controllers
      _ <- httpClient.expect[String](POST(
        Uri.fromString(s"${config.controllerUrl.head}/api/repos/$repositoryName/_synced").toTry.get,
        SynchronizedRequest(request.nodeUrl).asJson
      ))
      resp <- Ok()
    } yield resp
  }

}

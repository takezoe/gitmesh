package com.github.takezoe.gitmesh.controller.api

import cats.implicits._
import cats.effect.IO
import com.github.takezoe.gitmesh.controller.data.models.NodeRepositoryStatus
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.DataStore
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import com.github.takezoe.gitmesh.controller.util.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.client.dsl.io._

class Services(dataStore: DataStore, httpClient: Client[IO])(implicit val config: Config) {

  implicit private val log = LoggerFactory.getLogger(classOf[Services])
  implicit private val decoderForJoinNodeRequest = jsonOf[IO, JoinNodeRequest]
  implicit private val decoderForSynchronizedRequest = jsonOf[IO, SynchronizedRequest]

  def joinNode(req: Request[IO]): IO[Response[IO]] = {
    for {
      node   <- req.decodeJson[JoinNodeRequest]
      exists <- dataStore.existNode(node.url)
      _      <- if(exists){
        dataStore.updateNodeStatus(node.url, node.diskUsage)
      } else {
        dataStore.addNewNode(node.url, node.diskUsage, node.repos).map {
          _.collect { case (repo, false) =>
            httpClient.expect[String](DELETE(toUri(s"${node.url}/api/repos/${repo.name}"))).attempt.map {
              case Left(e) => log.error(s"Failed to delete repository: ${repo.name} on ${node.url}", e)
              case _ => ()
            }
          }
        }.flatMap(_.toList.sequence)
      }
      resp <- Ok()
    } yield resp
  }

  def listNodes(): IO[Response[IO]] = {
    for {
      nodes <- dataStore.allNodes()
      resp  <- Ok(Json.arr(nodes.map(_.asJson): _*))
    } yield resp
  }

  def listRepositories(): IO[Response[IO]] = {
    for {
      repos <- dataStore.allRepositories()
      resp  <- Ok(Json.arr(repos.map(_.asJson): _*))
    } yield resp
  }

  def deleteRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      repo <- dataStore.getRepositoryStatus(repositoryName)
      _    <- repo.map(_.nodes).getOrElse(Nil).map { node =>
        val action = for {
          _      <- httpClient.expect[String](DELETE(toUri(s"${node.url}/api/repos/$repositoryName")))
          result <- dataStore.deleteRepository(node.url, repositoryName)
        } yield result

        action.attempt.map {
          case Left(e) => log.error(s"Failed to delete repository: ${repositoryName} on ${node.url}", e)
          case _ => ()
        }
      }.toList.sequence
      _    <- dataStore.deleteRepository(repositoryName)
      resp <- Ok()
    } yield resp
  }

  def createRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      repo   <- dataStore.getRepositoryStatus(repositoryName)
      result <- if(repo.nonEmpty){
        IO.pure(Left(ErrorModel(Seq("Repository already exists."))))
      } else {
        for {
          allNodes <- dataStore.allNodes()
          nodes    <- IO.pure(allNodes.filter { _.diskUsage < config.maxDiskUsage }.sortBy { _.diskUsage }.take(config.replica))
          result   <- if(nodes.nonEmpty){
            RepositoryLock.execute(repositoryName, "create repository") {
              for {
                timestamp <- dataStore.insertRepository(repositoryName)
                _         <- nodes.map { node =>
                  val action = for {
                    // Create a repository on the node
                    _ <- httpClient.expect[String](POST(
                      toUri(s"${node.url}/api/repos/${repositoryName}"), (), Header("GITMESH-UPDATE-ID", timestamp.toString)
                    ))
                    // Insert to NODE_REPOSITORY
                    result <- dataStore.insertNodeRepository(node.url, repositoryName, NodeRepositoryStatus.Ready)
                  } yield result

                  action.attempt.map {
                    case Left(e) => log.error(s"Failed to create repository ${repositoryName} on ${node.url}", e)
                    case _ => ()
                  }
                }.toList.sequence
              } yield Right((): Unit)
            }
          } else {
            IO.pure(Left(ErrorModel(Seq("There are no nodes which can accommodate a new repository"))))
          }
        } yield result
      }
      resp <- result match {
        case Right(_) => Ok()
        case Left(e: ErrorModel) => BadRequest(e.asJson)
      }
    } yield resp

  }

  def repositorySynchronized(req: Request[IO], repositoryName: String): IO[Response[IO]] = {
    for {
      node <- req.decodeJson[SynchronizedRequest]
      _    <- dataStore.updateNodeRepository(node.nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
      _    <- RepositoryLock.unlock(repositoryName)
      resp <- Ok()
    } yield resp
  }

  def lockRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      _    <- RepositoryLock.lock(repositoryName, "Lock by API")
      resp <- Ok()
    } yield resp
  }

}


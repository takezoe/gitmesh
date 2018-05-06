package com.github.takezoe.gitmesh.controller.api

import cats.effect.IO
import com.github.takezoe.gitmesh.controller.data.models.NodeRepositoryStatus
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.DataStore
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import org.http4s.client.Client
import org.http4s.dsl.io._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.slf4j.LoggerFactory
import io.circe.generic.auto._
import io.circe.jawn.CirceSupportParser
import io.circe.syntax._
import org.http4s.client.dsl.io._

class Services(dataStore: DataStore, httpClient: Client[IO])(implicit val config: Config) {

  private val log = LoggerFactory.getLogger(classOf[Services])

  implicit val decoderForJoinNodeRequest = jsonOf[IO, JoinNodeRequest]
  implicit val decoderForSynchronizedRequest = jsonOf[IO, SynchronizedRequest]

  def joinNode(req: Request[IO]): IO[Response[IO]] = {
    for {
      node <- req.as[String].flatMap { json =>
        IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[JoinNodeRequest])
      }
      _ <- IO {
        if(dataStore.existNode(node.url)){
          dataStore.updateNodeStatus(node.url, node.diskUsage)
        } else {
          dataStore.addNewNode(node.url, node.diskUsage, node.repos)
        }
      }
      resp <- Ok()
    } yield resp
  }

  def listNodes(): IO[Response[IO]] = {
    for {
      nodes <- IO { dataStore.allNodes() }
      resp <- Ok(Json.arr(nodes.map(_.asJson): _*))
    } yield resp
  }

  def listRepositories(): IO[Response[IO]] = {
    for {
      repos <- IO { dataStore.allRepositories() }
      resp <- Ok(Json.arr(repos.map(_.asJson): _*))
    } yield resp
  }

  def deleteRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      _ <- IO {
        dataStore
          .getRepositoryStatus(repositoryName).map(_.nodes).getOrElse(Nil)
          .foreach { node =>
            try {
              // Delete a repository from the node
              //httpDelete[String](s"${node.url}/api/repos/$repositoryName")
              httpClient.expect[String](DELETE(
                Uri.fromString(s"${node.url}/api/repos/$repositoryName").toTry.get
              )).unsafeRunSync()
              // Delete from NODE_REPOSITORY
              dataStore.deleteRepository(node.url, repositoryName)
            } catch {
              case e: Exception => log.error(s"Failed to delete repository $repositoryName on ${node.url}", e)
            }
          }

        // Delete from REPOSITORY
        dataStore.deleteRepository(repositoryName)
      }
      resp <- Ok()
    } yield resp
  }

  def createRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      result <- IO {
        val repo = dataStore.getRepositoryStatus(repositoryName)
        if(repo.nonEmpty){
          Left(ErrorModel(Seq("Repository already exists.")))
        } else {
          val nodes = dataStore.allNodes()
            .filter { _.diskUsage < config.maxDiskUsage }
            .sortBy { _.diskUsage }
            .take(config.replica)

          if(nodes.nonEmpty){
            RepositoryLock.execute(repositoryName, "create repository"){
              // Insert to REPOSITORY and get timestamp
              val timestamp = dataStore.insertRepository(repositoryName)

              nodes.foreach { node =>
                try {
                  // Create a repository on the node
                  httpClient.expect[String](POST.apply(
                    Uri.fromString(s"${node.url}/api/repos/${repositoryName}").toTry.get,
                    (),
                    Header("GITMESH-UPDATE-ID", timestamp.toString)
                  )).unsafeRunSync()
                  // Insert to NODE_REPOSITORY
                  dataStore.insertNodeRepository(node.url, repositoryName, NodeRepositoryStatus.Ready)

                } catch {
                  case e: Exception => log.error(s"Failed to create repository $repositoryName on ${node.url}", e)
                }
              }

              Right((): Unit)
            }
          } else {
            Left(ErrorModel(Seq("There are no nodes which can accommodate a new repository")))
          }
        }
      }
      resp <- result match {
        case Right(_) => Ok()
        case Left(e: ErrorModel) => BadRequest(e.asJson)
      }
    } yield resp

  }

  def repositorySynchronized(req: Request[IO], repositoryName: String): IO[Response[IO]] = {
    for {
      node <- req.as[String].flatMap { json =>
        IO.fromEither(CirceSupportParser.parseFromString(json.trim).get.as[SynchronizedRequest])
      }
      _ <- IO {
        dataStore.updateNodeRepository(node.nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
        RepositoryLock.unlock(repositoryName)
      }
      resp <- Ok()
    } yield resp
  }

  def lockRepository(repositoryName: String): IO[Response[IO]] = {
    for {
      _ <- IO { RepositoryLock.lock(repositoryName, "Lock by API") }
      resp <- Ok()
    } yield resp
  }

}


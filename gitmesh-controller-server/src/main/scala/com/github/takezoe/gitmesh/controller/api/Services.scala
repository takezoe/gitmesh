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

  implicit private val log = LoggerFactory.getLogger(getClass)
  implicit private val decoderForJoinNodeRequest = jsonOf[IO, JoinNodeRequest]
  implicit private val decoderForSynchronizedRequest = jsonOf[IO, SynchronizedRequest]

  def joinNode(req: Request[IO]): IO[Response[IO]] = {
    req.as[JoinNodeRequest].flatMap { node =>
      if(dataStore.existNode(node.url)){
        dataStore.updateNodeStatus(node.url, node.diskUsage)
      } else {
        dataStore.addNewNode(node.url, node.diskUsage, node.repos).collect { case (repo, false) => repo }.foreach { repo =>
          httpClient.expect[String](DELETE(toUri(s"${node.url}/api/repos/${repo.name}"))).attempt.map {
            case Left(e) => log.error(s"Failed to delete repository: ${repo.name} on ${node.url}", e)
            case _ => ()
          }.unsafeRunSync()
        }
      }
      Ok()
    }
  }

  def listNodes(): IO[Response[IO]] = {
      Ok(Json.arr(dataStore.allNodes().map(_.asJson): _*))
  }

  def listRepositories(): IO[Response[IO]] = {
    Ok(Json.arr(dataStore.allRepositories().map(_.asJson): _*))
  }

  def deleteRepository(repositoryName: String): IO[Response[IO]] = {
    val repo = dataStore.getRepositoryStatus(repositoryName)
    repo.foreach(_.nodes.foreach { node =>
      httpClient.expect[String](DELETE(toUri(s"${node.url}/api/repos/$repositoryName"))).unsafeRunSync()
      dataStore.deleteRepository(node.url, repositoryName)
    })
    dataStore.deleteRepository(repositoryName)
    Ok()
  }

  def createRepository(repositoryName: String): IO[Response[IO]] = {
    val repo = dataStore.getRepositoryStatus(repositoryName)
    if(repo.nonEmpty){
      BadRequest(ErrorModel(Seq("Repository already exists.")).asJson)
    } else {
      val nodes = dataStore.allNodes().filter { _.diskUsage < config.maxDiskUsage }.sortBy { _.diskUsage }.take(config.replica)
      if(nodes.isEmpty){
        BadRequest(ErrorModel(Seq("There are no nodes which can accommodate a new repository.")).asJson)
      } else {
        RepositoryLock.execute(repositoryName, "create repository") {
          val timestamp = dataStore.insertRepository(repositoryName)
          nodes.map { node =>
            // Create a repository on the node
            httpClient.expect[String](POST(
              toUri(s"${node.url}/api/repos/${repositoryName}"), (), Header("GITMESH-UPDATE-ID", timestamp.toString)
            )).unsafeRunSync()

            // Insert to NODE_REPOSITORY
            dataStore.insertNodeRepository(node.url, repositoryName, NodeRepositoryStatus.Ready)
          }
        }
        Ok()
      }
    }
  }

  def repositorySynchronized(req: Request[IO], repositoryName: String): IO[Response[IO]] = {
    req.as[SynchronizedRequest].flatMap { node =>
      dataStore.updateNodeRepository(node.nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
      RepositoryLock.unlock(repositoryName)
      Ok()
    }
  }

  def lockRepository(repositoryName: String): IO[Response[IO]] = {
    RepositoryLock.lock(repositoryName, "Lock by API") match {
      case Right(_) => Ok()
      case Left(e)  => InternalServerError(e.getMessage)
    }
  }

}


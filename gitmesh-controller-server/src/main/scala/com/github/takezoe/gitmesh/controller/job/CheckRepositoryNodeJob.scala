package com.github.takezoe.gitmesh.controller.job

import cats.implicits._
import cats.effect.IO
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.DataStore
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, ControllerLock, RepositoryLock}
import com.github.takezoe.gitmesh.controller.util.syntax._
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s._
import org.http4s.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.client.dsl.io._
import org.slf4j.LoggerFactory

class CheckRepositoryNodeJob(implicit val config: Config, dataStore: DataStore, httpClient: Client[IO]) extends Runnable {

  private val log = LoggerFactory.getLogger(getClass)

  override def run(): Unit = {
    // Check dead nodes
    val timeout = System.currentTimeMillis() - config.deadDetectionPeriod.node

    println("** job **")

    val action: IO[_] = for {
      locked <- ControllerLock.runForMaster("**master**", config.url, config.deadDetectionPeriod.master)
      _      <- {
        println(locked)
        if(locked) IO.unit else IO.never
      }
      nodes  <- dataStore.allNodes()
      _      <- nodes.filter(_.timestamp < timeout).map { node =>
        log.warn(s"${node.url} is retired.")
        dataStore.removeNode(node.url)
      }.toList.sequence
      repos  <- dataStore.allRepositories()
      result <- IO {
        // Create replica
        repos.filter { x => x.nodes.size < config.replica }.foreach { x =>
          x.primaryNode.foreach { primaryNode =>
            createReplicas(primaryNode, x.name, x.timestamp, x.nodes.size)
          }
        }
      }
    } yield result

    action.unsafeRunSync()
  }

  private def createReplicas(primaryNode: String, repositoryName: String, timestamp: Long, enabledNodes: Int): Unit = {
    val lackOfReplicas = config.replica - enabledNodes

    (1 to lackOfReplicas).foreach { _ =>
      dataStore.getUrlOfAvailableNode(repositoryName).flatMap { _.map { nodeUrl =>
        log.info(s"Create replica of ${repositoryName} at $nodeUrl")
        if(timestamp == InitialRepositoryId){
          log.info("Create empty repository")
          RepositoryLock.execute(repositoryName, "create replica") {
            for {
              _ <- httpClient.expect[String](PUT(
                toUri(s"$nodeUrl/api/repos/${repositoryName}/_clone"),
                CloneRequest(primaryNode, true).asJson,
                Header("GITMESH-UPDATE-ID", timestamp.toString)
              ))
              // Insert a node record here because cloning an empty repository is proceeded as 1-phase.
              result <- dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
            } yield result
          }
        } else {
          log.info("Clone repository")
          for {
            _ <- httpClient.expect[String](PUT(
              toUri(s"$nodeUrl/api/repos/${repositoryName}/_clone"),
              CloneRequest(primaryNode, false).asJson,
              Header("GITMESH-UPDATE-ID", timestamp.toString)
            ))
            // Insert a node record as PREPARING status here, updated to READY later
            result <- dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Preparing)
          } yield result
        }
      }.getOrElse(IO.unit)}.unsafeRunSync()
    }
  }

}

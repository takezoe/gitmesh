package com.github.takezoe.gitmesh.controller.job

import akka.actor.Actor
import akka.event.Logging
import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.DataStore
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, ControllerLock, RepositoryLock}
import com.github.takezoe.resty.HttpClientSupport

class CheckRepositoryNodeActor(implicit val config: Config, dataStore: DataStore) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)
  //implicit override val httpClientConfig = config.httpClient

  override def receive = {
    case _ => {
      if(ControllerLock.runForMaster("**master**", config.url, config.deadDetectionPeriod.master)){
        // Check dead nodes
        val timeout = System.currentTimeMillis() - config.deadDetectionPeriod.node

        dataStore.allNodes().foreach { node =>
          if(node.timestamp < timeout){
            log.warning(s"${node.url} is retired.")
            dataStore.removeNode(node.url)
          }
        }

        // Create replica
        val repos = dataStore.allRepositories()

        repos.filter { x => x.nodes.size < config.replica }.foreach { x =>
          x.primaryNode.foreach { primaryNode =>
            createReplicas(primaryNode, x.name, x.timestamp, x.nodes.size)
          }
        }
      }
    }
  }

  private def createReplicas(primaryNode: String, repositoryName: String, timestamp: Long, enabledNodes: Int): Unit = {
    val lackOfReplicas = config.replica - enabledNodes

    (1 to lackOfReplicas).foreach { _ =>
      dataStore.getUrlOfAvailableNode(repositoryName).map { nodeUrl =>
        log.info(s"Create replica of ${repositoryName} at $nodeUrl")

        if(timestamp == InitialRepositoryId){
          log.info("Create empty repository")
          // Repository is empty
          RepositoryLock.execute(repositoryName, "create replica") {  // TODO need shared lock?
            httpPutJson(
              s"$nodeUrl/api/repos/${repositoryName}/_clone",
              CloneRequest(primaryNode, true),
              builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
            )
            // Insert a node record here because cloning an empty repository is proceeded as 1-phase.
            dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Ready)
          }
        } else {
          log.info("Clone repository")
          // Repository is not empty.
          httpPutJson(
            s"$nodeUrl/api/repos/${repositoryName}/_clone",
            CloneRequest(primaryNode, false),
            builder => { builder.addHeader("GITMESH-UPDATE-ID", timestamp.toString) }
          )
          // Insert a node record as PREPARING status here, updated to READY later
          dataStore.insertNodeRepository(nodeUrl, repositoryName, NodeRepositoryStatus.Preparing)
        }
      }
    }
  }

}
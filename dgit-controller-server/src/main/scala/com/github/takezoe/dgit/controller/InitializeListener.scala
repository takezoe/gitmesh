package com.github.takezoe.dgit.controller

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._
import akka.event.Logging

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global // TODO

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load() //(2, 0.9d)

    Resty.register(new APIController(config))

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config)), "tick")

  }

}

class CheckRepositoryNodeActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive = {
    case _ => {
      // Check died nodes
      val timeout = System.currentTimeMillis() - (5 * 60 * 1000)
      NodeManager.allNodes().foreach { case (node, status) =>
        if(status.timestamp < timeout){
          log.warning(s"$node is retired.")
          NodeManager.removeNode(node)
        }
      }

      // Add replica in parallel
      val futures = NodeManager.allRepositories()
        .filter { repository => repository.nodes.size < config.replica }
        .flatMap { repository =>
          (1 to config.replica - repository.nodes.size).flatMap { _ =>
            NodeManager.selectAvailableNode().map { replicaNode =>
              httpPutJsonAsync(
                s"$replicaNode/api/repos/${repository.name}",
                CloneRequest(s"${repository.primaryNode}/git/${repository.name}.git")
              ).map { result =>
                // Update node status
                NodeManager.allNodes()
                  .find { case (node, _) => node == replicaNode }
                  .foreach { case (node, status) =>
                    NodeManager.updateNodeStatus(node, status.diskUsage, status.repos :+ repository.name)
                  }
                result
              }
            }
          }
        }

      val f = Future.sequence(futures)

      // TODO Error handling
      val results = Await.result(f, 10.minutes)
      log.debug(s"Results of creating replicas: $results")
    }
  }
}

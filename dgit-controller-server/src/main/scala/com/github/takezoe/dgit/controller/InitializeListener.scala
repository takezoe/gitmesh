package com.github.takezoe.dgit.controller

import javax.servlet.{ServletContextEvent, ServletContextListener}
import javax.servlet.annotation.WebListener

import com.github.takezoe.resty._
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import akka.actor._
import models.CloneRequest

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global // TODO

@WebListener
class InitializeListener extends ServletContextListener {

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config(2, 0.9d)

    Resty.register(new APIController(config))

    val system = ActorSystem("mySystem")
    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config)), "tick")

  }

}

class CheckRepositoryNodeActor(config: Config) extends Actor with HttpClientSupport {
  override def receive = {
    case _ => {
      // Check died nodes
      val timeout = System.currentTimeMillis() - (5 * 60 * 1000)
      Nodes.allNodes().foreach { case (node, status) =>
        if(status.timestamp < timeout){
          println(s"$node is retired.") // TODO debug
          Nodes.removeNode(node)
        }
      }

      // Add replica in parallel
      val futures = Nodes.allRepositories()
        .filter { repository => repository.nodes.size < config.replica }
        .flatMap { repository =>
          (1 to config.replica - repository.nodes.size).flatMap { _ =>
            Nodes.selectAvailableNode().map { replicaNode =>
              httpPutJsonAsync(
                s"$replicaNode/api/repos/${repository.name}",
                CloneRequest(s"${repository.primaryNode}/git/${repository.name}.git")
              ).map { result =>
                // Update node status
                Nodes.allNodes()
                  .find { case (node, _) => node == replicaNode }
                  .foreach { case (node, status) =>
                    Nodes.updateNodeStatus(node, status.diskUsage, status.repos :+ repository.name)
                  }
                result
              }
            }
          }
        }

      val f = Future.sequence(futures)
      // TODO Error handling
      val results = Await.result(f, Duration.Inf)
      println("Results of creation replica: " + results) // TODO debug
    }
  }
}

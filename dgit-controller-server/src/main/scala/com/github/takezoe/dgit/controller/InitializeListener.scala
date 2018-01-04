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

  private val system = ActorSystem("mySystem")

  override def contextDestroyed(sce: ServletContextEvent): Unit = {
    val f = system.terminate()
    Await.result(f, 30.seconds)
  }

  override def contextInitialized(sce: ServletContextEvent): Unit = {
    val config = Config.load()

    Resty.register(new APIController(config))

    val scheduler = QuartzSchedulerExtension(system)
    scheduler.schedule("Every30Seconds", system.actorOf(Props(classOf[CheckRepositoryNodeActor], config)), "tick")

  }

}

class CheckRepositoryNodeActor(config: Config) extends Actor with HttpClientSupport {

  private val log = Logging(context.system, this)

  override def receive = {
    case _ => {
      // Check died nodes
      val timeout = System.currentTimeMillis() - (30 * 1000)

      NodeManager.allNodes().foreach { case (node, status) =>
        if(status.timestamp < timeout){
          log.warning(s"$node is retired.")
          NodeManager.removeNode(node)
        }
      }

      // Add replica if it's needed
      NodeManager.allRepositories()
        .filter { repository => repository.nodes.size < config.replica }
        .foreach { repository =>
          val lackOfReplicas = config.replica - repository.nodes.size
          if(lackOfReplicas > 0){
            RepositoryLock.execute(repository.name){
              (1 to config.replica - repository.nodes.size).flatMap { _ =>
                NodeManager.selectAvailableNode(repository.name).map { replicaNode =>
                  httpPutJson(
                    s"$replicaNode/api/repos/${repository.name}",
                    CloneRequest(s"${repository.primaryNode}/git/${repository.name}.git")
                  )
                  // Update node status
                  NodeManager.allNodes()
                    .find { case (node, _) => node == replicaNode }
                    .foreach { case (node, status) =>
                      NodeManager.updateNodeStatus(node, status.diskUsage, status.repos :+ repository.name)
                    }
                }
              }
            }
          }
        }
    }
  }
}

case class CloneRequest(source: String)

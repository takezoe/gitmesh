package com.github.takezoe.gitmesh.controller.data

import com.github.takezoe.gitmesh.controller.api.models._
import com.github.takezoe.gitmesh.controller.data.models._
import com.github.takezoe.gitmesh.controller.util.{Config, RepositoryLock}
import org.slf4j.LoggerFactory

class DataStore {

  private val db = Database.db
  import db._

  private val log = LoggerFactory.getLogger(getClass)

  def clearClusterStatus(): Unit = {
    db.transaction {
      db.run(quote { query[Repository].update(_.primaryNode -> lift(None: Option[String])) })
      db.run(quote { query[NodeRepository].delete })
      db.run(quote { query[Node].delete })
      db.run(quote { query[ExclusiveLock].delete })
    }
  }

  def existNode(nodeUrl: String): Boolean = {
    val count = db.transaction {
      db.run(quote { query[Node].filter(_.nodeUrl == lift(nodeUrl)).size })
    }
    count > 0
  }

  def addNewNode(nodeUrl: String, diskUsage: Double, repos: Seq[JoinNodeRepository])
                (implicit config: Config): Seq[(JoinNodeRepository, Boolean)] = {
    db.transaction {
      db.run(quote {
        query[Node].insert(lift(Node(
          nodeUrl        = nodeUrl,
          lastUpdateTime = System.currentTimeMillis(),
          diskUsage      = diskUsage
        )))
      })

      repos.map { repo =>
        val status = getRepositoryStatus(repo.name)
        status match {
          // add
          case Some(x) if x.timestamp == repo.timestamp && x.nodes.size < config.replica =>
            if(x.primaryNode.isEmpty){
              db.run(quote {
                query[Repository].filter(_.repositoryName == lift(repo.name)).update(_.primaryNode -> lift(Some(nodeUrl): Option[String]))
              })
            }
            db.run(quote {
              query[NodeRepository].insert(lift(NodeRepository(
                nodeUrl        = nodeUrl,
                repositoryName = repo.name,
                status         = NodeRepositoryStatus.Ready
              )))
            })
            (repo, true)

          // not add
          case _ => (repo, false)
        }
      }
    }
  }

  def updateNodeStatus(nodeUrl: String, diskUsage: Double): Unit = {
    db.transaction {
      db.run(quote {
        query[Node]
          .filter(t => t.nodeUrl == lift(nodeUrl))
          .update(_.lastUpdateTime -> lift(System.currentTimeMillis()), _.diskUsage -> lift(diskUsage))
      })
    }
  }

  def removeNode(nodeUrl: String)(implicit config: Config): Unit = {
    log.info(s"Remove node: $nodeUrl")

    db.transaction {
      val repos = db.run(quote {
        query[Repository].filter(_.primaryNode.contains(lift(nodeUrl))).map(_.repositoryName)
      })

      repos.foreach { repositoryName =>
        RepositoryLock.execute(repositoryName, "remove node") {
          val nextPrimaryNode = db.run(quote {
            query[NodeRepository]
              .filter(t => t.nodeUrl != lift(nodeUrl) && t.repositoryName == lift(repositoryName))
              .map(_.nodeUrl)
              .take(1)
          }).headOption

          nextPrimaryNode match {
            case Some(nextPrimaryNode) =>
              db.run(quote {
                query[Repository].filter(t => t.repositoryName == lift(repositoryName)).update(_.primaryNode -> lift(Some(nextPrimaryNode): Option[String]))
              })
            case None =>
              log.error(s"All nodes for $repositoryName has been retired.")
              db.run(quote {
                query[Repository].filter(t => t.repositoryName == lift(repositoryName)).update(_.primaryNode -> lift(None: Option[String]))
              })
          }
        }
      }

      db.run(quote { query[NodeRepository].filter(_.nodeUrl == lift(nodeUrl)).delete })
      db.run(quote { query[Node].filter(_.nodeUrl == lift(nodeUrl)).delete })
    }
  }

  def allNodes(): Seq[NodeStatus] = {
    val nodes = db.run(quote {
      query[Node].leftJoin(query[NodeRepository]).on((n, nr) => n.nodeUrl == nr.nodeUrl)
    })

    nodes
      .groupBy { case (node, _) => node }
      .collect { case (node, nodes) =>
        NodeStatus(
          url       = node.nodeUrl,
          timestamp = node.lastUpdateTime,
          diskUsage = node.diskUsage,
          repos     = nodes.collect { case (_, Some(x)) => NodeStatusRepository(x.repositoryName, x.status) }
        )
      }.toSeq
  }

  /**
   * NOTE: This method must be used only in the repository lock.
   */
  def updateRepositoryTimestamp(repositoryName: String, timestamp: Long): Unit = {
    db.transaction {
      db.run(db.quote {
        query[Repository].filter(_.repositoryName == lift(repositoryName)).update(_.lastUpdateTime -> lift(timestamp))
      })
    }
  }

  def deleteRepository(nodeUrl: String, repositoryName: String): Unit = {
    db.transaction {
      db.run(quote { query[NodeRepository].filter(t => t.nodeUrl == lift(nodeUrl) && t.repositoryName == lift(repositoryName)).delete })
    }
  }

  def deleteRepository(repositoryName: String): Unit = {
    db.transaction {
      db.run(quote { query[Repository].filter(t => t.repositoryName == lift(repositoryName)).delete })
    }
  }

  def insertRepository(repositoryName: String): Long = {
    db.transaction {
      db.run(quote {
        query[Repository].insert(lift(Repository(
          repositoryName = repositoryName,
          primaryNode    = None,
          lastUpdateTime = InitialRepositoryId
        )))
      })
    }
    InitialRepositoryId
  }

  def insertNodeRepository(nodeUrl: String, repositoryName: String, status: String): Unit = {
    val repo = getRepositoryStatus(repositoryName)

    db.transaction {
      if(repo.map(_.primaryNode.isEmpty).getOrElse(false)){
        db.run(quote {
          query[Repository].filter(_.repositoryName == lift(repositoryName)).update(_.primaryNode -> lift(Some(nodeUrl): Option[String]))
        })
      }

      db.run(quote {
        query[NodeRepository].filter(t => t.nodeUrl == lift(nodeUrl) && t.repositoryName == lift(repositoryName)).delete
      })
      db.run(quote {
        query[NodeRepository].insert(lift(NodeRepository(
          nodeUrl        = nodeUrl,
          repositoryName = repositoryName,
          status         = status
        )))
      })
    }
  }

  def updateNodeRepository(nodeUrl: String, repositoryName: String, status: String): Unit = {
    db.transaction {
      db.run(quote {
        query[NodeRepository]
          .filter(t => t.nodeUrl == lift(nodeUrl) && t.repositoryName == lift(repositoryName))
          .update(_.status -> lift(status))
      })
    }
  }

  def getRepositoryStatus(repositoryName: String): Option[RepositoryInfo] = {
    db.transaction {
      val repo = db.run(quote {
        query[Repository].filter(t => t.repositoryName == lift(repositoryName))
      }).headOption

      val nodes = db.run(quote {
        query[NodeRepository].filter(t => t.repositoryName == lift(repositoryName))
      })

      repo.map { repo =>
        RepositoryInfo(repositoryName, repo.primaryNode, repo.lastUpdateTime, nodes.map(x => RepositoryNodeInfo(x.nodeUrl, x.status)))
      }
    }
  }

  def allRepositories(): Seq[RepositoryInfo] = {
    val repos = db.run(quote {
      query[Repository].leftJoin(query[NodeRepository]).on((r, nr) => r.repositoryName == nr.repositoryName)
    })

    repos
      .groupBy { case (repo, _) => repo }
      .collect { case (repo, nodes) =>
        RepositoryInfo(
          name        = repo.repositoryName,
          primaryNode = repo.primaryNode,
          timestamp   = repo.lastUpdateTime,
          nodes       = nodes.collect { case (_, Some(node)) => RepositoryNodeInfo(node.nodeUrl, node.status) }
        )
      }.toSeq
  }

  def getUrlOfAvailableNode(repositoryName: String)(implicit config: Config): Option[String] = {
    db.run(quote {
      query[Node]
        .filter(t =>
          !query[NodeRepository].filter(_.repositoryName == lift(repositoryName)).map(_.nodeUrl).contains(t.nodeUrl)
            && t.diskUsage < lift(config.maxDiskUsage))
        .sortBy(_.diskUsage)
        .map(_.nodeUrl)
        .take(1)
    }).headOption
  }

}

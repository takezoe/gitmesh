package com.github.takezoe.dgit.controller

object models {

  import com.github.takezoe.tranquil._
  import java.sql.ResultSet

  case class Node(nodeUrl: String, lastUpdateTime: Long, diskUsage: Double)

  class Nodes extends TableDef[Node]("NODE") {
    val nodeUrl        = new Column[String](this, "NODE_URL")
    val lastUpdateTime = new Column[Long](this, "LAST_UPDATE_TIME")
    val diskUsage      = new Column[Double](this, "DISK_USAGE")

    override def toModel(rs: ResultSet): Node = {
      Node(nodeUrl.get(rs), lastUpdateTime.get(rs), diskUsage.get(rs))
    }
  }

  case class Repository(repositoryName: String, primaryNode: Option[String], lastUpdateTime: Long)

  class Repositories extends TableDef[Repository]("REPOSITORY") {
    val repositoryName = new Column[String](this, "REPOSITORY_NAME")
    val primaryNode    = new OptionalColumn[String](this, "PRIMARY_NODE")
    val lastUpdateTime = new Column[Long](this, "LAST_UPDATE_TIME")

    override def toModel(rs: ResultSet): Repository = {
      Repository(repositoryName.get(rs), primaryNode.get(rs), lastUpdateTime.get(rs))
    }
  }

  case class NodeRepository(nodeUrl: String, repositoryName: String)

  class NodeRepositories extends TableDef[NodeRepository]("NODE_REPOSITORY") {
    val nodeUrl        = new Column[String](this, "NODE_URL")
    val repositoryName = new Column[String](this, "REPOSITORY_NAME")

    override def toModel(rs: ResultSet): NodeRepository = {
      NodeRepository(nodeUrl.get(rs), repositoryName.get(rs))
    }
  }

  case class ExclusiveLock(lockKey: String, comment: Option[String], lockTime: Long)

  class ExclusiveLocks extends TableDef[ExclusiveLock]("EXCLUSIVE_LOCK") {
    val lockKey  = new Column[String](this, "LOCK_KEY")
    val comment  = new OptionalColumn[String](this, "COMMENT")
    val lockTime = new Column[Long](this, "LOCK_TIME")

    override def toModel(rs: ResultSet): ExclusiveLock = {
      ExclusiveLock(lockKey.get(rs), comment.get(rs), lockTime.get(rs))
    }
  }

  val Nodes = new SingleTableAction[Nodes, Node](new Nodes())
  val Repositories = new SingleTableAction[Repositories, Repository](new Repositories())
  val NodeRepositories = new SingleTableAction[NodeRepositories, NodeRepository](new NodeRepositories())
  val ExclusiveLocks = new SingleTableAction[ExclusiveLocks, ExclusiveLock](new ExclusiveLocks())

}

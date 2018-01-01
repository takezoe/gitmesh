package com.github.takezoe.dgit.repository

import java.io.File

import com.github.takezoe.resty._
import org.eclipse.jgit.lib.RepositoryBuilder
import Utils._
import com.github.takezoe.dgit.repository.models.Result

class APIController(config: Config) {

  @Action(method="GET", path = "/api/healthCheck")
  def healthCheck(): Result = {
    Result("OK")
  }

  @Action(method="POST", path = "/api/create/{name}")
  def createRepository(name: String): Unit = {
    println("Creating repository: " + name)
    using(new RepositoryBuilder().setGitDir(new File(config.dir, name)).setBare.build){ repository =>
      repository.create(true)
      val config = repository.getConfig
      config.setBoolean("http", null, "receivepack", true)
      config.save
    }
  }

  def cloneRepository() = {

  }

}

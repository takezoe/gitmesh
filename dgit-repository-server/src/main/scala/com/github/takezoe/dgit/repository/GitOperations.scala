package com.github.takezoe.dgit.repository

import java.io.File

import org.eclipse.jgit.api.Git
import syntax._
import org.eclipse.jgit.lib.RepositoryBuilder

trait GitOperations {

  def createRepository(name: String)(implicit config: Config): Unit = {
    using(new RepositoryBuilder().setGitDir(new File(config.directory, name)).setBare.build){ repository =>
      repository.create(true)
      defining(repository.getConfig){ config =>
        config.setBoolean("http", null, "receivepack", true)
        config.save
      }
    }
  }

  def cloneRepository(name: String, sourceUrl: String)(implicit config: Config): Unit = {
    using(Git.cloneRepository().setBare(true).setURI(sourceUrl)
             .setDirectory(new File(config.directory, name)).setCloneAllBranches(true).call()){ git =>
      using(git.getRepository){ repository =>
        defining(repository.getConfig){ config =>
          config.setBoolean("http", null, "receivepack", true)
          config.save
        }
      }
    }
  }

}

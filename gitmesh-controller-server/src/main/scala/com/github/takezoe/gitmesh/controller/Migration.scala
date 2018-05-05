package com.github.takezoe.gitmesh.controller

import io.github.gitbucket.solidbase.migration.LiquibaseMigration
import io.github.gitbucket.solidbase.model.{Module, Version}

object Migration extends Module("gitmesh",
  new Version("1.0.0", new LiquibaseMigration("update/gitmesh-database-1.0.0.xml"))
)


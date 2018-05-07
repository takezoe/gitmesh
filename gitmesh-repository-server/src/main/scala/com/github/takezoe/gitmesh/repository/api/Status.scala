package com.github.takezoe.gitmesh.repository.api

case class Status(url: String, diskUsage: Double, repos: Seq[String])

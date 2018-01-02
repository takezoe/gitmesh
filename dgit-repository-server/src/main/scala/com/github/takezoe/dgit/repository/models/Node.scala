package com.github.takezoe.dgit.repository.models

case class Node(endpoint: String, diskUsage: Double, repos: Seq[String])

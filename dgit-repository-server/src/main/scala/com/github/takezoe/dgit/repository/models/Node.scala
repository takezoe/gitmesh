package com.github.takezoe.dgit.repository.models

case class Node(node: String, diskUsage: Double, repos: Seq[String])

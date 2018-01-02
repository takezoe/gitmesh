package com.github.takezoe.dgit.controller.models

case class Node(endpoint: String, diskUsage: Double, repos: Seq[String])

package com.github.takezoe.dgit.controller.models

case class Node(node: String, diskUsage: Double, repos: Seq[String])

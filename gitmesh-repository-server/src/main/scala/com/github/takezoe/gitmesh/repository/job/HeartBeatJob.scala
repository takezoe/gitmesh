package com.github.takezoe.gitmesh.repository.job

import java.io.File

import cats.effect.IO
import com.github.takezoe.gitmesh.repository.util._
import com.github.takezoe.gitmesh.repository.util.syntax._
import org.apache.commons.io.FileUtils
import org.http4s.client.Client
import org.slf4j.LoggerFactory
import org.http4s.dsl.io._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.client.dsl.io._

class HeartBeatJob(httpClient: Client[IO], config: Config) extends Runnable {

  private val log = LoggerFactory.getLogger(getClass)
  private val urls = config.controllerUrl.map { url => s"$url/api/nodes/notify" }

  override def run(): Unit = {
    val rootDir = new File(config.directory)
    val diskUsage = 1.0d - (rootDir.getFreeSpace.toDouble / rootDir.getTotalSpace.toDouble)
    val repos = rootDir.listFiles(_.isDirectory).toSeq.flatMap { dir =>
      val file = new File(rootDir, s"${dir.getName}.id")
      if(file.exists){
        val timestamp = FileUtils.readFileToString(new File(rootDir, s"${dir.getName}.id"), "UTF-8").toLong
        Some(HeartBeatRepository(dir.getName, timestamp))
      } else {
        None
      }
    }

    firstSuccess(urls.map { url =>
      httpClient.expect[String](POST(toUri(url), HeartBeatRequest(config.url, diskUsage, repos).asJson))
    }).unsafeRunSync()
  }

}

case class HeartBeatRequest(url: String, diskUsage: Double, repos: Seq[HeartBeatRepository])
case class HeartBeatRepository(name: String, timestamp: Long)
package com.github.takezoe.gitmesh.repository.job

import java.io.File

import akka.actor.Actor
import com.github.takezoe.gitmesh.repository.util.Config
import com.github.takezoe.resty.{ErrorModel, HttpClientSupport, RequestTarget}
import com.github.takezoe.resty.util.JsonUtils
import okhttp3.{Request, RequestBody}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

class HeartBeatActor(notifier: HeartBeatSender) extends Actor with HttpClientSupport {

  override def receive: Receive = {
    case _ => notifier.send()
  }

}

class HeartBeatSender(config: Config) extends HttpClientSupport {

  implicit override val httpClientConfig = config.httpClient

  private val log = LoggerFactory.getLogger(classOf[HeartBeatSender])
  private val urls = config.controllerUrl.map { url => s"$url/api/nodes/notify" }

  def send(): Unit = {
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

    httpPostJson[String](urls, HeartBeatRequest(config.url, diskUsage, repos)) match {
      case Right(_) => // success
      case Left(e) => log.error(e.errors.mkString("\n"))
    }
  }

  override def httpPostJson[T](target: RequestTarget, doc: AnyRef, configurer: Request.Builder => Unit = (builder) => ())(implicit c: ClassTag[T]): Either[ErrorModel, T] = {
    target.execute(httpClient, (url, builder) => {
      builder.url(url).post(RequestBody.create(HttpClientSupport.ContentType_JSON, JsonUtils.serialize(doc) + "\n"))
      configurer(builder)
    }, c.runtimeClass)
  }


}

case class HeartBeatRequest(url: String, diskUsage: Double, repos: Seq[HeartBeatRepository])
case class HeartBeatRepository(name: String, timestamp: Long)
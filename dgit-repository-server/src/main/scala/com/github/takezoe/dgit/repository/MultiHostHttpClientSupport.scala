package com.github.takezoe.dgit.repository

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}

import com.github.takezoe.resty._
import com.github.takezoe.resty.util.JsonUtils
import okhttp3.{Request, RequestBody}

import scala.reflect.ClassTag

trait MultiHostHttpClientSupport extends HttpClientSupport {

  private def tryAll[T](urls: RoundRobinUrls)(f: String => T): T = {
    while(true){
      var count = 0
      val size = urls.availableSize
      urls.next() match {
        case None => throw new RuntimeException("No available url!")
        case Some(url) => try {
          f(url)
          count = count + 1
        } catch {
          case _: Exception if count < size => urls.failed(url)
        }
      }
    }
    ???
  }

  def httpPostJsonMulti[T](urls: RoundRobinUrls, doc: AnyRef, configurer: Request.Builder => Unit = (builder) => ())
                          (implicit c: ClassTag[T]): Either[ErrorModel, T] = {
    tryAll(urls){ url =>
      val builder = new Request.Builder().url(url)
        .post(RequestBody.create(HttpClientSupport.ContentType_JSON, JsonUtils.serialize(doc)))

      execute(builder, configurer, c.runtimeClass)
    }
  }

}

class RoundRobinUrls(urls: Seq[String]){
  private val counter = new AtomicInteger(0)
  private val targetUrls = urls.map(url => new Url(url))

  def availableSize: Int = targetUrls.count(_.available)

  def next(): Option[String] = {
    val availableUrls = targetUrls.collect { case x if x.available => x.url }
    if(availableUrls.isEmpty){
      None
    } else {
      Some(availableUrls(counter.getAndIncrement() % availableUrls.size))
    }
  }

  def failed(url: String): Unit ={
    targetUrls.find(_.url == url).foreach(_.failed())
  }
}

class Url(val url: String){
  val errors = new AtomicInteger(0)
  val timestamp = new AtomicLong(0)

  def available: Boolean = {
    if(errors.get < 3){
      true
    } else {
      val currentTimestamp = System.currentTimeMillis
      if(timestamp.get > currentTimestamp - (5 * 60 * 1000)){ // try again after 5 minutes
        errors.set(0)
        true
      } else {
        false
      }
    }
  }

  def failed(): Unit = {
    errors.incrementAndGet()
    timestamp.set(System.currentTimeMillis)
  }
}
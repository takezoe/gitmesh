package com.github.takezoe.dgit.repository

import com.github.takezoe.resty._
import com.github.takezoe.resty.util.JsonUtils
import okhttp3.{Request, RequestBody}

import scala.reflect.ClassTag

trait MultiHostHttpClientSupport extends HttpClientSupport {

  private def retry[T](urls: Seq[String])(f: String => T): T = {
    urls.zipWithIndex.foreach { case (url, i) =>
      try {
        return f(url)
      } catch {
        case _: Exception if i < urls.size - 1 => ()
      }
    }
    ???
  }

  def httpPostJsonMulti[T](urls: Seq[String], doc: AnyRef, configurer: Request.Builder => Unit = (builder) => ())(implicit c: ClassTag[T]): Either[ErrorModel, T] = {
    retry(urls){ url =>
      val builder = new Request.Builder().url(url)
        .post(RequestBody.create(HttpClientSupport.ContentType_JSON, JsonUtils.serialize(doc)))

      execute(builder, configurer, c.runtimeClass)
    }
  }

}

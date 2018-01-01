package com.github.takezoe.dgit.controller

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.collection.JavaConverters._
import okhttp3.{OkHttpClient, Request}
import org.apache.commons.io.IOUtils
import Utils._

class GitRepositoryProxyServer extends HttpServlet {

  private val client = new OkHttpClient()

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    println("**** doPost ****")
    println(req.getRequestURI)
    println(req.getRequestURL)

    val path = req.getRequestURI
    val repositoryName = path.replaceAll("(^/git/)|(\\.git($|/.*))", "")

    println("repo name: " + repositoryName)

    val contextPath = req.getServletContext.getContextPath

    println(contextPath)
    println("--")

    val nodes = Nodes.selectNodes(repositoryName)
    if(nodes.nonEmpty){
      // TODO Proxy to all selected hosts
    } else {
      // TODO NotFound
    }
  }

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    println("**** doGet ****")
    println(req.getRequestURI)
    println(req.getRequestURL)

    val path = req.getRequestURI
    val repositoryName = path.replaceAll("(^/git/)|(\\.git($|/.*))", "")

    println("repo name: " + repositoryName)

    val contextPath = req.getServletContext.getContextPath

    println(contextPath)
    println("--")

    Nodes.selectNode(repositoryName).map { node =>
      val url = "http://" + node.host + ":" + node.port + path
      val builder = new Request.Builder()
          .url(url)

      req.getHeaderNames.asScala.foreach { name =>
        builder.addHeader(name, req.getHeader(name))
      }

      val request = builder.build()
      val response = client.newCall(request).execute()

      response.headers().names().asScala.foreach { name =>
        resp.setHeader(name, response.header(name))
      }

      using(response.body().byteStream(), resp.getOutputStream){ (in, out) =>
        IOUtils.copy(in, out)
      }

    }.getOrElse {
      // TODO NotFound

    }
  }

}

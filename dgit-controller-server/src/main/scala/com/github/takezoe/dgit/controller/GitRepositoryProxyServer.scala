package com.github.takezoe.dgit.controller

import java.io.{File, FileOutputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.collection.JavaConverters._
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.apache.commons.io.{FileUtils, IOUtils}
import Utils._
import org.slf4j.LoggerFactory

class GitRepositoryProxyServer extends HttpServlet {

  private val log = LoggerFactory.getLogger(classOf[GitRepositoryProxyServer])
  private val client = new OkHttpClient()

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val path = req.getRequestURI
    val queryString = req.getQueryString
    val repositoryName = path.replaceAll("(^/git/)|(\\.git($|/.*))", "")

    RepositoryLock.execute(repositoryName) {
      val nodes = NodeManager.selectNodes(repositoryName)

      if (nodes.nonEmpty) {
        val tmpFile = File.createTempFile("dgit", "tmpfile")
        try {
          using(req.getInputStream, new FileOutputStream(tmpFile)) { (in, out) =>
            IOUtils.copy(in, out)
          }

          var responded = false

          nodes.foreach { node =>
            val builder = new Request.Builder().url(node + path + (if (queryString == null) "" else "?" + queryString))

            req.getHeaderNames.asScala.foreach { name =>
              builder.addHeader(name, req.getHeader(name))
            }
            builder.post(RequestBody.create(MediaType.parse(req.getContentType), tmpFile))

            val request = builder.build()

            try {
              val response = client.newCall(request).execute()
              if (responded == false) {
                response.headers().names().asScala.foreach { name =>
                  resp.setHeader(name, response.header(name))
                }
                using(response.body().byteStream(), resp.getOutputStream) { (in, out) =>
                  IOUtils.copy(in, out)
                }
                responded = true
              }
            } catch {
              // If request failed remove the node
              case e: Exception =>
                log.error(s"Remove node $node by error: ${e.toString}")
                NodeManager.removeNode(node)
            }
          }
        } finally {
          FileUtils.forceDelete(tmpFile)
        }
      } else {
        throw new RuntimeException(s"There are no available nodes for $repositoryName")
      }
    }
  }

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val path = req.getRequestURI
    val queryString = req.getQueryString
    val repositoryName = path.replaceAll("(^/git/)|(\\.git($|/.*))", "")

    NodeManager.selectNode(repositoryName).map { node =>
      val builder = new Request.Builder().url(node + path + (if(queryString == null) "" else "?" + queryString))

      req.getHeaderNames.asScala.foreach { name =>
        builder.addHeader(name, req.getHeader(name))
      }

      val request = builder.build()

      try {
        val response = client.newCall(request).execute()

        response.headers().names().asScala.foreach { name =>
          resp.setHeader(name, response.header(name))
        }

        using(response.body().byteStream(), resp.getOutputStream){ (in, out) =>
          IOUtils.copy(in, out)
        }
      } catch {
        // If request failed, remove the node and try other nodes
        case e: Exception =>
          log.error(s"Remove node $node by error: ${e.toString}")
          NodeManager.removeNode(node)
          doGet(req, resp)
      }
    }.getOrElse {
      throw new RuntimeException(s"There are no available nodes for $repositoryName")
    }
  }

}

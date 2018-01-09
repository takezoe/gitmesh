package com.github.takezoe.dgit.controller

import java.io.{File, FileOutputStream}
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.collection.JavaConverters._
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.slf4j.LoggerFactory

import syntax._

class GitRepositoryProxyServer extends HttpServlet {

  private val log = LoggerFactory.getLogger(classOf[GitRepositoryProxyServer])
  private val client = new OkHttpClient()

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val path = req.getRequestURI
    val queryString = req.getQueryString
    val repositoryName = path.replaceAll("(^/git/)|(\\.git($|/.*))", "")

    RepositoryLock.execute(repositoryName) {
      val timestamp = System.currentTimeMillis
      val nodes = Database.withTransaction { implicit conn =>
        NodeManager.updateRepositoryTimestamp(repositoryName, timestamp)
        NodeManager.getNodeUrlsOfRepository(repositoryName)
      }

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
            builder.addHeader("DGIT-UPDATE-ID", timestamp.toString)

            val request = builder.build()

            try {
              val response = client.newCall(request).execute()
              if (responded == false) {
                resp.setStatus(response.code())
                response.headers().names().asScala.foreach { name =>
                  resp.setHeader(name, response.header(name))
                }
                using(response.body().byteStream(), resp.getOutputStream) { (in, out) =>
                  IOUtils.copy(in, out)
                  out.flush()
                }
                responded = true
              }
            } catch {
              // If request failed remove the node
              case e: Exception =>
                log.error(s"Remove node $node by error: ${e.toString}")
                Database.withTransaction { implicit conn =>
                  NodeManager.removeNode(node)
                }
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

    val primaryNode = Database.withSession { implicit conn =>
      NodeManager.getRepositoryStatus(repositoryName).map(_.primaryNode).flatten
    }

    primaryNode.map { nodeUrl =>
      val builder = new Request.Builder().url(nodeUrl + path + (if(queryString == null) "" else "?" + queryString))

      req.getHeaderNames.asScala.foreach { name =>
        builder.addHeader(name, req.getHeader(name))
      }

      val request = builder.build()

      try {
        val response = client.newCall(request).execute()

        resp.setStatus(response.code())
        response.headers().names().asScala.foreach { name =>
          resp.setHeader(name, response.header(name))
        }

        using(response.body().byteStream(), resp.getOutputStream){ (in, out) =>
          IOUtils.copy(in, out)
          out.flush()
        }
      } catch {
        // If request failed, remove the node and try other nodes
        case e: Exception =>
          log.error(s"Remove node $nodeUrl by error: ${e.toString}")
          Database.withTransaction { implicit conn =>
            NodeManager.removeNode(nodeUrl)
          }
          doGet(req, resp)
      }
    }.getOrElse {
      // TODO 404
      throw new RuntimeException(s"There are no available nodes for $repositoryName")
    }
  }

}

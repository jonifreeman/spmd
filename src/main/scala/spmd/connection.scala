package spmd

import java.io._
import java.net._
import scala.io.Source

object Connection {
  class Client(addr: String, port: Int) {
    lazy val socket = new Socket(addr, port) // FIXME close when client closes
    lazy val out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
    lazy val in = socket.getInputStream

    def send(attrs: List[Attr]) = {
      out.write(attrs.mkString(";") + "\n")
      out.flush
      Response.from(in)
    }
  }
  
  trait Server {
    def actions: PartialFunction[Request, Response]
    def exitHandler: Socket => Any
    val notFound: PartialFunction[Request, Response] = { case _ => new Response(Nil) }

    def start {
      val serverSocket = new ServerSocket(6128)
    
      while (true) {
        // FIXME use a pool
        new Handler(serverSocket.accept).start
      }
    }

    class Handler(clientSocket: Socket) extends Thread {
      override def run {
        val out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream))
        val in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))

        try {
          while (true) {
            val req = Request(Request.from(in), clientSocket)
            val action = actions.orElse(notFound)
            val res = action(req)
            out.write(res.toString)
            out.flush
          }
        } catch {
          case e: IOException => // ok, client closed the connection
        } finally {
          exitHandler(clientSocket)
        }
      }
    }
  }

  class Response(val attrs: List[List[Attr]]) {
    override def toString = attrs.map(_.mkString(";")).mkString("\t") + "\n"
  }

  object Response {
    def from(in: InputStream) = {
      val bodyOrNull = new BufferedReader(new InputStreamReader(in)).readLine
      val body = if (bodyOrNull == null) "" else bodyOrNull
      val lines = body.split("\t").filter(!_.isEmpty).toList
      val resp = lines.map(l => l.split(";").toList).map(attrs => attrs.map(Attr.from(_))).toList
      new Response(resp)
    }
  }

  case class Request(attrs: List[Attr], socket: Socket)

  object Request {
    def from(in: BufferedReader) = {
      val bodyOrNull = in.readLine
      val body = if (bodyOrNull == null) "" else bodyOrNull
      body.split(";").map(Attr.from(_)).toList
    }
  }

  case class Attr(key: String, value: String) {
    override def toString = key + ":" + value
  }

  object Attr {
    def from(s: String) = {
      val parts = s.split(":")
      if (parts.size == 1) Attr(parts(0), "") else Attr(parts(0), parts(1))
    }
  }
}

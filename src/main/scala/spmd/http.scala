package spmd

import java.io._
import java.net._
import scala.io.Source

object Http {
  class Client(addr: String, port: Int) {
    def put(url: String, body: String) = Request.from(PUT, url, body)
    def get(url: String) = Request.from(GET, url, "")
    
    def send(req: Request) = {
      val socket = new Socket(addr, port)
      val out = new PrintWriter(socket.getOutputStream, true)
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      out.write(req.method + " " + req.url.mkString("/") + " HTTP/1.1\r\n")
      out.flush
      read(in)
    }

    def read(in: BufferedReader) = {
      def read0(acc: String): String = in.readLine match {
        case null => acc
        case s => read0(acc + "\n" + s)
      }
      read0("")
    }
  }
  
  trait Server {    
    def actions: PartialFunction[Request, Response]
    val notFound: PartialFunction[Request, Response] = { case _ => Response(NotFound, "", false) }

    def start {
      val serverSocket = new ServerSocket(6128)
    
      while (true) {
        new Handler(serverSocket.accept).start
      }
    }

    class Handler(clientSocket: Socket) extends scala.actors.Actor {
      def act {
        val out = new PrintWriter(clientSocket.getOutputStream, true)
        val in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
        val req = Request.fromRequestLine(in.readLine)
        val action = actions.orElse(notFound)
        val res = action(req)
        println(res.toString)
        out.write(res.toString)
        out.flush

        if (res.blocking) {
          Source.fromInputStream(clientSocket.getInputStream).getLines
        }
        else clientSocket.close
      }
    }
  }

  case class Response(status: Status, body: String, blocking: Boolean) {
    override def toString = 
      "HTTP/1.0 " + status.code + " " + status.toString + "\r\n" +
      "Content-Type: application/json\r\n" +
      body + "\r\n"
  }

  case class Request(method: Method, url: List[String], body: String)
  case object Request {
    def fromRequestLine(s: String) = {
      val elems = s.split(" ")
      (elems(0), split(elems(1))) match {
        case ("PUT", url) => new Request(PUT, url, "")
        case ("GET", url) => new Request(GET, url, "")
        case _ => error("unknown method: " + s)
      }
    }

    def from(method: Method, url: String, body: String) = new Request(method, split(url), body)
    private def split(url: String) = url.split("/").filter(!_.isEmpty).toList
  }
  
  sealed abstract class Method
  case object PUT extends Method
  case object GET extends Method

  sealed abstract class Status(val code: Int)
  case object OK extends Status(200) {
    override def toString = "OK"
  }
  case object NotFound extends Status(404) {
    override def toString = "Not Found"
  }
}

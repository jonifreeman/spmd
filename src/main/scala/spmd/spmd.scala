package spmd

object Spmd {
  def main(args: Array[String]) = {
    // Start listening
    Server
    // Establish client conn
    // API, json?
    // Integrate to console
  }
}

trait Client {
//  def names = names(inet.getHostName)
//  def names(host: HostName)
//  def registerNode(name: Name, port: Int)
}

// PUT /nodes/{node}/{ip}/{port} HTTP/1.1
object Server {
  import java.io._
  import java.net._
  import scala.actors.Actor

  val serverSocket = new ServerSocket(6128)

  while (true) {
    new Handler(serverSocket.accept).start    
  }

  class Handler(clientSocket: Socket) extends Actor {
    def act {
      val out = new PrintWriter(clientSocket.getOutputStream, true)
      val in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
      val req = Request.fromRequestLine(in.readLine)
      val res = req match {
        case Request(PUT, "nodes" :: node :: ip :: port :: Nil) => Response(OK, "{ ok }")
        case Request(GET, "nodes" :: node :: ip :: port :: Nil) => Response(OK, "{ ok }") // Remove
        case _ => Response(NotFound, "")
      }
      println(res.toString)
      out.write(res.toString)
      out.flush
      out.close
    }
  }

  case class Response(status: Status, body: String) {
    override def toString = 
      "HTTP/1.0 " + status.code + " " + status.toString + "\r\n" +
      "Content-Type: application/json\r\n" +
      body + "\r\n"
  }

  case class Request(method: Method, url: List[String])
  case object Request {
    def fromRequestLine(s: String) = {
      val elems = s.split(" ")
      (elems(0), elems(1).split("/").filter(!_.isEmpty).toList) match {
        case ("PUT", url) => new Request(PUT, url)
        case ("GET", url) => new Request(GET, url)
        case _ => error("unknown method: " + s)
      }
    }
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

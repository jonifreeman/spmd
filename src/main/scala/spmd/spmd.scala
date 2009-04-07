package spmd


/*
 * http://www.nabble.com/epmd-not-cleaning-up-node-name-if-node-is-down.-td18996856.html
The normal and correct behaviour is that a node is unregistered from
epmd when it is shutdown.
each Erlang node that register to epmd establishes and keep an open
tcp connection towards epmd.
When that connection is broken the Erlang node is unregistered.
*/

object Daemon {
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
      val request = Request.fromRequestLine(in.readLine)
      request match {
        case Request(PUT, "nodes" :: node :: ip :: port :: Nil) => println(node)
        case Request(GET, "nodes" :: node :: ip :: port :: Nil) => println(node) // FIXME remove
      }
    }
  }

  case class Request(method: Method, url: List[String])
  case object Request {
    def fromRequestLine(s: String) = {
      val elems = s.split(" ")
      (elems(0), elems(1).split("/").filter(!_.isEmpty).toList) match {
        case ("PUT", url) => Request(PUT, url)
        case ("GET", url) => Request(GET, url)
        case _ => error("unknown method: " + s)
      }
    }
  }
  
  sealed abstract class Method
  case object PUT extends Method
  case object GET extends Method
}

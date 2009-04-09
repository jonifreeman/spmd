package spmd

object Spmd extends Http.Server {
  import scala.actors.Actor._
  import Http._

  val nodes = actor {
    loop {
      react {
        case 'nodes => ""
      }
    }
  }

  def actions = {
    case Request(PUT, "nodes" :: name :: ip :: port :: Nil, _) => Response(OK, "{ ok }", true)
    case Request(GET, "nodes" :: Nil, _) => Response(OK, """{ "nodes": []  }""", false)
  }

  def main(args: Array[String]) = {
    start
  }
}

case class Node(name: String, address: String, port: Int)

trait Client {
  val http = new Http.Client("localhost", 6128)

//  def names(host: HostName)
  def names = http.send(http.get("/nodes"))

  def registerNode(node: Node) = {
    val t = new Thread(new Runnable {
      def run {
        val req = http.put("/nodes/" + node.name + "/" + node.address + "/" + node.port, "")
        http.send(req)
      }
    })
    t.setDaemon(true)
    t.start
  }
}

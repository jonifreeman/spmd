package spmd

object Spmd extends Http.Server {
  import Http._
  import scala.collection.mutable.{HashMap, SynchronizedMap}

  val nodes = new HashMap[String, Node]() with SynchronizedMap[String, Node]

  def actions = {
    case Request(PUT, "nodes" :: name :: address :: port :: Nil, _) => 
      nodes += (name -> Node(name, address, port.toInt))
      new Response(OK, "{ ok }", Some(() => { nodes -= name }))
    case Request(GET, "nodes" :: Nil, _) => 
      new Response(OK, "{ \"nodes\": [" + nodes.values.map(_.toJson).mkString(",") + "]  }", None)
  }

  def main(args: Array[String]) = {
    start
  }
}

case class Node(name: String, address: String, port: Int) {
  def toJson = " { \"name\": \""+name+"\", \"address\": \""+address+"\", \"port\": "+port+" } "
}

case object Node {
  import scala.util.parsing.json.JSON

  def fromPairs(pairs: List[(Any, Any)]): Node = {
    def valueOf[A](name: String) = pairs.find(_._1 == name).get._2.asInstanceOf[A]
    Node(valueOf[String]("name"), valueOf[String]("address"), valueOf[Double]("port").toInt)
  }

  def fromJson(json: String): List[Node] = {
    val nodes = JSON.parseFull(json) map { case ns: Map[String, List[List[(Any, Any)]]] =>
      ns("nodes").map(fromPairs _)
    }
    nodes.getOrElse(List())
  }
}

trait SpmdClient {
  val http = new Http.Client("localhost", 6128)

//  def nodes(host: HostName)
  def nodes: List[Node] = Node.fromJson(http.send(http.get("/nodes")))

  def registerNode(name: String, address: String): Node = {
    val port = scala.actors.remote.TcpService.generatePort
    val t = new Thread(new Runnable {
      def run {
        val req = http.put("/nodes/" + name + "/" + address + "/" + port, "")
        http.send(req)
      }
    })
    t.setDaemon(true)
    t.start
    Node(name, address, port)
  }

  def findNode(name: String) = nodes.find(_.name == name)
}

object Console extends SpmdClient {
  def main(args: Array[String]) = {
    val name = args.toList.dropWhile(_ != "-name")
    if (name isEmpty) {
      println("-name argument is required")
      exit(0)
    }
    registerNode(name(1), java.net.InetAddress.getLocalHost.getHostAddress)
    scala.tools.nsc.MainGenericRunner.main(Array())
  }
}

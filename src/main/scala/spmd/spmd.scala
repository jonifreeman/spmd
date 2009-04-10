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
  def toJson = " { \"name\": "+name+", \"address\": "+address+", \"port\": "+port+" } "
}

trait SpmdClient {
  val http = new Http.Client("localhost", 6128)

//  def nodes(host: HostName)
  def nodes = {
    val nodes = http.send(http.get("/nodes"))
    println(nodes)
  }

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

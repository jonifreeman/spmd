package spmd

object Spmd extends Http.Server with SpmdClient {
  import Http._
  import scala.collection.mutable.{HashMap, SynchronizedMap}

  val knownNodes = new HashMap[String, Node]() with SynchronizedMap[String, Node]

  def actions = {
    case Request(PUT, "nodes" :: name :: address :: port :: Nil, _) => 
      knownNodes += (name -> Node(name, address, port.toInt))
      new Response(OK, "{\"ok\":\"" + name + "\"}", Some(() => { knownNodes -= name }))
    case Request(GET, "nodes" :: Nil, _) => 
      new Response(OK, "{\"nodes\":[" + knownNodes.values.map(_.toJson).mkString(",") + "]}", None)
    case Request(PUT, "kill" :: Nil, _) => 
      exit(0)
      new Response(OK, "", None)
  }

  def main(args: Array[String]) = {
    try {
      if (args.toList.exists(_ == "-kill"))
        http.send(http.put("/kill", ""))
      else if (args.toList.exists(_ == "-names"))
        println(http.send(http.get("/nodes")))
      else 
        start
    } catch {
      case e: java.net.ConnectException => println("spmd is not running")
    }
  }
}

case class Node(name: String, address: String, port: Int) {
  def toJson = "{\"name\":\""+name+"\", \"address\":\""+address+"\", \"port\":"+port+"}"
}

object Node {
  import scala.util.parsing.json.JSON

  def fromPairs(pairs: List[(Any, Any)]): Node = {
    def valueOf[A](name: String) = pairs.find(_._1 == name).get._2.asInstanceOf[A]
    Node(valueOf[String]("name"), valueOf[String]("address"), valueOf[Double]("port").toInt)
  }

  def fromJson(json: String): List[Node] = {
    val nodes = JSON.parseFull(json).get.asInstanceOf[Map[String, Any]]("nodes")
    nodes match {
      case ns: List[List[(Any, Any)]] => ns.map(fromPairs _)
      case _ => List()
    }
  }
}

trait SpmdClient {
  val http = new Http.Client("localhost", 6128)

//  def nodes(host: HostName)
  def nodes: List[Node] = Node.fromJson(http.send(http.get("/nodes")))

  def registerNode(name: String, address: String): Node = {
    if (findNode(name).isDefined) error("Node with name '" + name + "' already exists.")
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
    start(args)
    scala.tools.nsc.MainGenericRunner.main(Array())
  }

  def start(args: Array[String]) = {
    val name = args.toList.dropWhile(_ != "-name")
    if (name isEmpty) {
      println("-name argument is required")
      exit(0)
    }
    node = name(1)
    registerNode(name(1), java.net.InetAddress.getLocalHost.getCanonicalHostName)
    Global.start
  }

  var node = "nonode@nohost"
}

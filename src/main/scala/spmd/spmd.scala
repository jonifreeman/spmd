package spmd

import Connection._

object Spmd extends Server with SpmdClient {
  import scala.collection.mutable.{HashMap, SynchronizedMap}

  val knownNodes = new HashMap[Address, Node]() with SynchronizedMap[Address, Node]

  def exitHandler = (a: Address) => { knownNodes -= a }
  def actions = {
    case Request(client, Attr("name", n) :: Attr("address", a) :: Attr("port", p) :: Attr("monitorPort", m) :: Nil) => 
      knownNodes += (client -> Node(n, a, p.toInt, m.toInt))
      Response(List(Attr("name", n)) :: Nil)
    case Request(_, Attr("nodes", _) :: Nil) => Response(knownNodes.values.toList.map(_.toAttrs))
    case Request(_, Attr("kill", _) :: Nil) => exit(0)
  }

  def main(args: Array[String]) = {
    try {
      if (args.toList.exists(_ == "-kill"))
        conn.send(List(Attr("kill", "")))
      else if (args.toList.exists(_ == "-names"))
        println(conn.send(List(Attr("nodes", ""))))
      else 
        start
    } catch {
      case e: java.net.ConnectException => println("spmd is not running")
    }
  }
}

// monitorPort can be removed if the port for Actor comm could be utilized
case class Node(name: String, address: String, port: Int, monitorPort: Int) {
  def toAttrs = List(Attr("name", name), Attr("address", address), 
                     Attr("port", port.toString), Attr("monitorPort", monitorPort.toString))
}

object Node {
  def fromAttrs(attrs: List[Attr]): Node = {
    def valueOf[A](name: String) = attrs.find(_.key == name).get.value
    Node(valueOf[String]("name"), valueOf[String]("address"), 
         valueOf[Double]("port").toInt, valueOf[Double]("monitorPort").toInt)
  }

  def fromResponse(res: Response): List[Node] = res.attrs.map(fromAttrs)
}

trait SpmdClient {
  val conn = new Client("localhost", 6128)

  def nodes(hostname: String): List[Node] = {
    val remote = new Client(hostname, 6128)
    try {
      nodes(remote)
    } finally {
      remote.close
    }
  }
  def nodes: List[Node] = nodes(conn)
  private def nodes(c: Client) = Node.fromResponse(conn.send(List(Attr("nodes", ""))))

  def registerNode(name: String, address: String): Node = {
    if (findNode(name).isDefined) error("Node with name '" + name + "' already exists.")
    val port = scala.actors.remote.TcpService.generatePort
    val monitorPort = scala.actors.remote.TcpService.generatePort
    val node = Node(name, address, port, monitorPort)
    Util.spawnDaemon { conn.send(node.toAttrs) }
    node
  }

  def findNode(name: String): Option[Node] = {
    def findByName(nodeName: String, nodes: List[Node]) = nodes.find(_.name == nodeName)
    if (name.contains('@')) {
      val elems = name.split('@')
      findByName(elems(0), nodes(elems(1)))
    }
    else findByName(name, nodes)
  }
}

// FIXME move to own file 
object Console extends SpmdClient {
  def main(args: Array[String]) = {
    start(args)
    scala.tools.nsc.MainGenericRunner.main(Array())
  }

  def start(args: Array[String]) = {
    def getopt(opt: String) = {
      val value = args.toList.dropWhile(_ != opt)
      if (value isEmpty) None else value.drop(1).firstOption
    }

    val name = getopt("-name").getOrElse {
      println("-name argument is required")
      exit(0)
    }
    node = registerNode(name, java.net.InetAddress.getLocalHost.getCanonicalHostName)
    Global.start
    NetAdm.start

    val script = getopt("-s")
    script.foreach { s => 
      val clazz = Class.forName(s)
      clazz.getMethod("main", classOf[Array[String]]).invoke(null, args.asInstanceOf[AnyRef])
    }
  }

  var node = Node("nonode", "nohost", -1, -1)
}

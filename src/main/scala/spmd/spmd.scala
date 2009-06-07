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
        send(List(Attr("kill", "")))
      else if (args.toList.exists(_ == "-names"))
        println(send(List(Attr("nodes", ""))))
      else if (args.toList.exists(_ == "-help"))
        println("""
                |-kill    - shutdown running spmd
                |-names   - list all nodes registered to this spmd
                |-help    - this help
                """.stripMargin)
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
  private val conn = new Client("localhost", 6128)

  def nodes(hostname: String): List[Node] = Node.fromResponse(send(List(Attr("nodes", "")), hostname))
  def send(attrs: List[Attr]): Response = send(attrs, "localhost")

  private def send(attrs: List[Attr], hostname: String): Response = { 
    val client = new Client(hostname, 6128)
    try {
      client.send(attrs)
    } finally {
      client.close
    }
  }
  def nodes: List[Node] = nodes("localhost")

  def registerNode(name: String, address: String): Node = {
    if (findNode(name).isDefined) error("Node with name '" + name + "' already exists.")
    val port = scala.actors.remote.TcpService.generatePort
    val monitorPort = scala.actors.remote.TcpService.generatePort
    val node = Node(name, address, port, monitorPort)
    Util.spawnDaemon { conn.send(node.toAttrs) }
    node
  }

  def findNode(name: String): Option[Node] = {
    val (nodeName, hostname) = fullName(name)
    nodes(hostname).find(_.name == nodeName)
  }

  protected def fullName(nodeName: String) = {
    if (nodeName.contains('@')) {
      val elems = nodeName.split('@')
      (elems(0), (elems(1)))
    }
    else (nodeName, java.net.InetAddress.getLocalHost.getCanonicalHostName)
  }
}

// FIXME move to own file + better programmatic API
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
    val (nodeName, hostname) = fullName(name)
    node = registerNode(nodeName, hostname)
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

package spmd

object NetAdm extends Log {
  import scala.collection.mutable.LinkedHashSet
  import scala.actors.Actor._
  import scala.actors.TIMEOUT
  import spmd.RemoteActor._

  private val knownNodes = new LinkedHashSet[Node]

  def start = {
    Util.spawnDaemon { Monitor.start }
    NetAdmActor.start
  }

  object NetAdmActor extends scala.actors.Actor {
    def act = {
      register('net_adm, this)
      loop { receive { 
        case Ping(other) => 
          newKnownNode(other)
          reply(Pong(Console.node)) 
        case NewNode(other) => newKnownNode(other)
        case NodeDown(other) => debug("node down: " + other); knownNodes -= other
      }}
    }
  }

  def ping(nodeName: String): PingResponse = {
    Console.findNode(nodeName) match {
      case Some(remote) =>
        val targetNetAdm = select('net_adm, remote)
        targetNetAdm ! Ping(Console.node)
        self.receiveWithin(1000) {
          case pong @ Pong(_) => 
            knownNodes.foreach { n =>
              select('net_adm, n) ! NewNode(remote)
              targetNetAdm ! NewNode(n)
            }
            NetAdmActor ! NewNode(remote)
            pong
          case TIMEOUT => Pang("timeout")
        }
      case None => Pang("no such node")
    }
  }

  def node: Node = Console.node
  def nodes: List[Node] = knownNodes.toList
  def monitorNode(nodeName: String): PingResponse = ping(nodeName) match {
    case p @ Pong(remote) => Monitor.monitorNode(remote); p
    case p @ Pang(_) => p
  }

  private def newKnownNode(other: Node) = 
    if (other != Console.node && !knownNodes.contains(other)) {
      knownNodes += other 
      debug("starting to monitor new node " + other)
      Monitor.monitorNode(other)
    }

  case class Ping(other: Node)
  case class NewNode(other: Node)

  sealed abstract class PingResponse
  case class Pong(node: Node) extends PingResponse
  case class Pang(cause: String) extends PingResponse

  case class NodeDown(other: Node)

  private object Monitor extends Connection.Server {
    import scala.actors.Actor
    import scala.actors.Actor._
    import scala.collection.mutable.{HashMap, SynchronizedMap, ArrayBuffer, SynchronizedBuffer}
    import Connection._

    val monitoredNodes = new HashMap[Address, Node]() with SynchronizedMap[Address, Node]
    val monitors = new HashMap[Address, List[Actor]]() with SynchronizedMap[Address, List[Actor]]
    val outlinks = new ArrayBuffer[Client]() with SynchronizedBuffer[Client]

    def monitorNode(remote: Node) {
      require(remote != Console.node)
      val addrOfMonitoredNode = 
        if (!connectionEstablished(remote)) requestConnectionFrom(remote)
        else addrOf(remote)
      val listeners = monitors.getOrElse(addrOfMonitoredNode, List())
      monitors + (addrOfMonitoredNode -> (self :: listeners))
      monitoredNodes + (addrOfMonitoredNode -> remote)
    }

    private def connectionEstablished(remote: Node) = monitoredNodes.values.contains(remote)
    private def addrOf(remote: Node) = (for ((a, n) <- monitoredNodes if n == remote) yield a).toList.head

    private def requestConnectionFrom(monitoredNode: Node): Address = {
      val client = new Client(monitoredNode.address, monitoredNode.monitorPort)
      val addr = client.send(Console.node.toAttrs) match {
        case Response(List(List(Attr("address", a), Attr("port", p)))) => Address(a, p.toInt)
        case Response(x) => error(x.toString)
      }
      client.close
      addr
    }

    override val port = Console.node.monitorPort

    def exitHandler = (addrOfCrashedNode: Address) => { 
      monitors(addrOfCrashedNode).foreach { monitorActor =>
        val crashedNode = monitoredNodes(addrOfCrashedNode)
        monitorActor ! NodeDown(crashedNode)
      }
      monitors -= addrOfCrashedNode
      monitoredNodes -= addrOfCrashedNode
    }

    def actions = {
      case Request(_, Attr("name", n) :: Attr("address", a) :: Attr("port", p) :: Attr("monitorPort", m) :: Nil) => 
        val monitor = Node(n, a, p.toInt, m.toInt)
        val outlink = new Client(monitor.address, monitor.monitorPort)
        outlinks += outlink
        outlink.send(Nil)
      case Request(client, _) => Response(client.toAttrs :: Nil)
    }
  }
}

package spmd

object NetAdm extends scala.actors.Actor {
  import scala.collection.mutable.LinkedHashSet
  import scala.actors.Actor._
  import scala.actors.TIMEOUT
  import spmd.RemoteActor._

  private val knownNodes = new LinkedHashSet[Node]

  override def start = {
    Util.spawnDaemon { Monitor.start }
    super.start
  }

  def act = {
    register('net_adm, this)    
    loop { receive { 
      case Ping(other) => 
        newKnownNode(other)
        reply(Pong(Console.node)) 
      case NewNode(node) => newKnownNode(node)
    }}
  }

  def ping(nodeName: String): PingResponse = {
    Console.findNode(nodeName) match {
      case Some(node) =>
        val targetNetAdm = select('net_adm, node)
        targetNetAdm ! Ping(Console.node)
        self.receiveWithin(1000) {
          case pong @ Pong(_) => 
            knownNodes.foreach { n =>
              select('net_adm, n) ! NewNode(node)
              targetNetAdm ! NewNode(n)
            }
            NetAdm ! NewNode(node)
            pong
          case TIMEOUT => Pang("timeout")
        }
      case None => Pang("no such node")
    }
  }

  def nodes: List[Node] = knownNodes.toList
  def monitorNode(nodeName: String): PingResponse = ping(nodeName) match {
    case p @ Pong(node) => Monitor.monitorNode(node); p
    case p @ Pang(_) => p
  }

  private def newKnownNode(other: Node) = 
    if (other != Console.node) knownNodes += other 

  case class Ping(other: Node)
  case class NewNode(node: Node)

  sealed abstract class PingResponse
  case class Pong(node: Node) extends PingResponse
  case class Pang(cause: String) extends PingResponse

  private object Monitor extends Connection.Server {
    import scala.actors.Actor
    import scala.actors.Actor._
    import scala.collection.mutable.{HashMap, SynchronizedMap}
    import Connection._

    val monitoredNodes = new HashMap[Address, Node]() with SynchronizedMap[Address, Node]
    val monitors = new HashMap[Address, List[Actor]]() with SynchronizedMap[Address, List[Actor]]

    def monitorNode(node: Node) {
      require(node != Console.node)
      val addrOfMonitoredNode = 
        if (!connectionEstablished(node)) requestConnectionFrom(node)
        else addrOf(node)
      val listeners = monitors.getOrElse(addrOfMonitoredNode, List())
      monitors + (addrOfMonitoredNode -> (self :: listeners))
      monitoredNodes + (addrOfMonitoredNode -> node)
    }

    private def connectionEstablished(node: Node) = monitoredNodes.values.contains(node)
    private def addrOf(node: Node) = (for ((a, n) <- monitoredNodes if n == node) yield a).toList.head

    // FIXME this request should be closed
    private def requestConnectionFrom(monitoredNode: Node): Address =
      new Client(monitoredNode.address, monitoredNode.monitorPort).send(Console.node.toAttrs) match {
        case Response(List(List(Attr("address", a), Attr("port", p)))) => Address(a, p.toInt)
        case Response(x) => error(x.toString)
      }

    override val port = Console.node.monitorPort

    def exitHandler = (addrOfCrashedNode: Address) => { 
      monitors(addrOfCrashedNode).foreach { monitorActor =>
        val crashedNode = monitoredNodes(addrOfCrashedNode)
        monitorActor ! ('nodedown, crashedNode)
      }
      monitors -= addrOfCrashedNode
      monitoredNodes -= addrOfCrashedNode
    }

    def actions = {
      case Request(_, Attr("name", n) :: Attr("address", a) :: Attr("port", p) :: Attr("monitorPort", m) :: Nil) => 
        val monitor = Node(n, a, p.toInt, m.toInt)
        new Client(monitor.address, monitor.monitorPort).send(Nil)
      case Request(client, _) => Response(client.toAttrs :: Nil)
    }
  }
}


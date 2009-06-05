package spmd

object NetAdm extends scala.actors.Actor {
  import scala.collection.mutable.LinkedHashSet
  import scala.actors.Actor._
  import scala.actors.TIMEOUT
  import spmd.RemoteActor._

  private val knownNodes = new LinkedHashSet[Node]

  def act = {
    register('net_adm, this)    
    loop { receive { 
      case Ping(other) => 
        newKnownNode(other)
        reply(Pong) 
      case NewNode(node) => newKnownNode(node)
    }}
  }

  def ping(nodeName: String): PingResponse = {
    Console.findNode(nodeName) match {
      case Some(node) =>
        val targetNetAdm = select('net_adm, node)
        targetNetAdm ! Ping(Console.node)
        self.receiveWithin(1000) {
          case pong @ Pong => 
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

  private def newKnownNode(other: Node) = 
    if (other != Console.node) knownNodes += other 

  case class Ping(other: Node)
  case class NewNode(node: Node)

  sealed abstract class PingResponse
  case object Pong extends PingResponse
  case class Pang(cause: String) extends PingResponse
}

object Monitor extends Connection.Server {
  import scala.actors.Actor
  import scala.actors.Actor._
  import scala.collection.mutable.{HashMap, SynchronizedMap}
  import Connection._

  val monitors = new HashMap[Node, List[Actor]]() with SynchronizedMap[Node, List[Actor]]

  def monitorNode(node: Node) = {
    require(node != Console.node)
    if (!monitors.contains(node)) connectTo(node)
    val listeners = monitors.getOrElse(node, List())
    monitors + (node -> (self :: listeners))
  }

  private def connectTo(node: Node) {
    Util.spawnDaemon {
      new Client(node.address, node.monitorPort).send(Nil)
    }
  }

  override val port = Console.node.monitorPort

  def exitHandler = (a: Address) => { 
    println("exit")
    // FIXME send {nodedown,n2@nipert}
  }

  def actions = {
    case r @ Request(_, _) => Response(Nil)
  }
}

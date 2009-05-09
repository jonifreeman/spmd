package spmd

object NetAdm extends scala.actors.Actor {
  import scala.actors.Actor._
  import scala.actors.TIMEOUT
  import spmd.RemoteActor._

  val knownNodes = new scala.collection.mutable.ListBuffer[Node]

  def act = {
    register('net_adm, this)    
    loop { receive { 
      case Ping(other: Node) => knownNodes += other; reply(Pong) 
      case GetNodes => knownNodes
    }}
  }

  start

  def ping(nodeName: String): PingResponse = {
    Console.findNode(nodeName) match {
      case Some(node) =>
        val targetNetAdm = select('net_adm, node)
        targetNetAdm ! Ping(Console.node)
        self.receiveWithin(1000) {
          case pong @ Pong => knownNodes += node; pong
          case TIMEOUT => Pang("timout")
        }
      case None => Pang("no such node")
    }
  }

  def nodes: List[Node] = knownNodes.toList

  case class Ping(other: Node)
  case object GetNodes

  sealed abstract class PingResponse
  case object Pong extends PingResponse
  case class Pang(cause: String) extends PingResponse
}

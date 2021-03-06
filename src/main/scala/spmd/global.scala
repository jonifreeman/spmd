package spmd

object Global extends SpmdClient {
  import scala.actors.Actor._
  import scala.actors.remote.{RemoteActor => RActor, Node => RNode}

  def register(name: Symbol) = nameServer(Console.node) ! RegisterName(name)

  def whereIsName(name: Symbol): Option[Node] = nodes.find { node =>
    (nameServer(node) !? HasName(name)).asInstanceOf[Boolean]
  }

  private def nameServer(node: Node) = 
    RActor.select(RNode(node.address, node.port), 'globalNameServer)

  //def registeredNames = ()

  def start = actor {
    def loop(names: Set[Symbol]): Unit = receive {
      case RegisterName(name) => loop(names + name)
      case UnregisterName(name) => loop(names - name)
      case HasName(name) => reply(names.contains(name)); loop(names)
    }
    spmd.RemoteActor.register('globalNameServer, self)
    loop(Set())
  }

  case class RegisterName(name: Symbol)
  case class UnregisterName(name: Symbol)
  case class HasName(name: Symbol)
}

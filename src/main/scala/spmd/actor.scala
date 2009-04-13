package spmd

/**
 * An actor is made remotely accessible by registering it using a name:
 * <pre>
 * import scala.actors.Actor._
 * import spmd.RemoteActor._
 * actor {
 *   register('myName, self)
 *   loop { receive { case x => println("got " + x) }}
 * }
 * </pre>
 *
 * It can be accessed from a different node by selecting it in the following way: 
 * <pre>
 * import spmd.RemoteActor._
 * val a = select('myName)
 * a ! "hello"
 * </pre>
 */
object RemoteActor extends SpmdClient {
  import scala.actors.{Actor, AbstractActor}
  import scala.actors.remote.{RemoteActor => RActor, Node => RNode}

  def register(name: Symbol, actor: Actor) = {
    RActor.classLoader = null // This line can be removed when 2.8 is released (issue #1686)
    findNode(Console.node).foreach { localNode =>
      RActor.alive(localNode.port)
      RActor.register(name, actor)
      Global.register(name)
    }
  }

  def select(name: Symbol): AbstractActor = Global.whereIsName(name) match {
    case Some(node) => RActor.select(RNode(node.address, node.port), name)
    case None => error("no such name '" + name + "'")
  }
}

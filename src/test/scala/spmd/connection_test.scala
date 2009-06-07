package spmd

import scala.actors.Actor._
import spmd.RemoteActor.{register, select}
import spmd.NetAdm._

/** Use script test.sh to run this test.
 */
object Test {
  def main(args: Array[String]) = {
    spmd.Console.start(Array("-name", "testcoordinator"))
    `Three nodes should form a cluster transitively when pinging any two nodes`
  }

  def `Three nodes should form a cluster transitively when pinging any two nodes` = {
    val Pong(node1) = ping("testnode1")
    val Pong(node2) = ping("testnode2")

    val nodes0 = node :: nodes
    val nodes1 = (select('test_actor, node1) !? 'nodes).asInstanceOf[List[Node]]
    val nodes2 = (select('test_actor, node2) !? 'nodes).asInstanceOf[List[Node]]

    def verify(ns: List[Node]) = {
      assert(ns.length == 3)
      assert(ns.find(_.name == "testcoordinator").isDefined)
      assert(ns.find(_.name == "testnode1").isDefined)
      assert(ns.find(_.name == "testnode2").isDefined)
    }

    List(nodes0, nodes1, nodes2).foreach(verify(_))
    ok("test ok")
  }

  private def ok(msg: String) = {
    print(scala.Console.GREEN)
    println(msg)
    print(scala.Console.RESET)
  }
}


object TestNode {
  def main(args: Array[String]) = {
    spmd.Console.start(Array("-name", args(1)))
    println("starting " + node)
    actor {
      register('test_actor, self)
      loop {
        react {
          case 'nodes => reply(node :: nodes)
        }
      }
    }
  }
}

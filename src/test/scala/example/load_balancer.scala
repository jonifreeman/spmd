package example

import scala.actors.Actor
import scala.actors.Actor._
import spmd.RemoteActor._
import spmd.NetAdm

/**
 * 1. Start workers
 *    bin/node -cp target/classes:target/test-classes -s example.Worker -name w1
 *    bin/node -cp target/classes:target/test-classes -s example.Worker -name w2
 *    ...
 *
 * 2. Start load balancer
 *    bin/node -cp target/classes:target/test-classes -s example.LoadBalancer -name lb
 *
 * 3. Ping lb from worker nodes to join the cluster
 *    spmd.NetAdm.ping("lb")
 *
 * 4. Start new workers and kill old ones...
 */
object LoadBalancer {
  def main(args: Array[String]) = {
    roundRobin(0)
  }
  
  private def roundRobin(counter: Int): Unit = {
    val nodes = NetAdm.nodes
    if (nodes isEmpty) {
      println("no nodes")
      Thread.sleep(1000)
      roundRobin(counter)
    } else {
      val node = nodes(counter % nodes.size)
      val worker = select('worker, node)
      worker ! Work
      Thread.sleep(20)
      roundRobin(if (counter < Int.MaxValue) counter + 1 else 0)
    }
  }
}

object Worker {
  def main(args: Array[String]) = {
    actor {
      var count = 0
      register('worker, self)
      loop {
        react {
          case Work => 
            count = count + 1 
            if (count % 50 == 0) println("working hard @ " + NetAdm.node + " (" + count + " items done.)")
          case x => println("unknown message " + x)
        }
      }
    }
  }
}

case object Work

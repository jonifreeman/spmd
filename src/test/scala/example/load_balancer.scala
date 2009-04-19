package example

import scala.actors.Actor
import scala.actors.Actor._
import spmd.RemoteActor._
import spmd.Console

/**
 * 1. Start workers
 *    bin/node -cp target/classes:target/test-classes -s example.Worker -name w1
 *    bin/node -cp target/classes:target/test-classes -s example.Worker -name w2
 *    ...
 *
 * 2. Start load balancer
 *    scala -cp target/classes:target/test-classes example.LoadBalancer
 *
 * 3. Start new workers and kill old ones...
 */
object LoadBalancer {
  def main(args: Array[String]) = {
    roundRobin(0)
  }
  
  private def roundRobin(counter: Int): Unit = {
    val nodes = Console.nodes
    if (nodes isEmpty) error("no nodes")
    val node = nodes(counter % nodes.size)
    val worker = select('worker, node)
    worker ! Work
    Thread.sleep(20)
    roundRobin(if (counter < Int.MaxValue) counter + 1 else 0)
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
            if (count % 50 == 0) println("working hard @ " + Console.node + " (" + count + " items done.)")
          case x => println("unknown message " + x)
        }
      }
    }
  }
}

case object Work

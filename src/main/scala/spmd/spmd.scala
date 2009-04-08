package spmd

object Spmd {
  def main(args: Array[String]) = {
    Http.Server.start
  }
}

trait Client {
  import scala.actors.remote.Node

//  def names = names(inet.getHostName)
//  def names(host: HostName)
  def registerNode(name: String, node: Node) = {
    val t = new Thread(new Runnable {
      def run {
        val http = new Http.Client("localhost", 6128)
        val req = http.put("/nodes/" + name + "/" + node.address + "/" + node.port, "")
        http.send(req)
      }
    })
    t.setDaemon(true)
    t.start
  }
}

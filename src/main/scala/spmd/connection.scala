package spmd

import java.io._
import java.net._
import scala.io.Source

object Connection {
  class Client(addr: String, port: Int) {
    lazy val socket = {
      val s = new Socket(addr, port)
      s.setKeepAlive(true)
      s
    }
    lazy val out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream))
    lazy val in = socket.getInputStream

    def send(attrs: List[Attr]) = {
      out.write(attrs.mkString(";") + "\n")
      out.flush
      Response.from(in)
    }

    /** Closes the connection in a controlled way. Server will not execute exitHandler.
     */
    def close = {
      send(List(Attr("_close", "now")))
      out.close
      in.close
      socket.close
    }
  }
  
  trait Server {
    def actions: PartialFunction[Request, Response]
    def exitHandler: Address => Any
    val notFound: PartialFunction[Request, Response] = { case _ => new Response(Nil) }
    val port = 6128

    def start {
      val serverSocket = new ServerSocket(port)
    
      while (true) {
        // FIXME use a pool
        new Handler(serverSocket.accept).start
      }
    }

    class Handler(clientSocket: Socket) extends Thread {
      override def run {
        val out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream))
        val in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
        val clientAddress = Address.from(clientSocket)

        def loop: Unit = 
          Request.from(in, clientAddress) match {
            case Request(_, List(Attr("_close", "now"))) => 
            case req =>
              val action = actions.orElse(notFound)
              val res = action(req)
              out.write(res.toString)
              out.flush
              loop
          }
          
        try {
          loop
        } catch {
          case t => exitHandler(clientAddress)
        }
        out.close
        in.close
        clientSocket.close
      }
    }
  }

  case class Address(address: String, port: Int) {
    def toAttrs = List(Attr("address", address), Attr("port", port.toString))
  }

  case object Address {
    def from(socket: Socket) = {
      val inetAddr = socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
      new Address(inetAddr.getAddress.getHostName, inetAddr.getPort)
    }
  }

  case class Response(val attrs: List[List[Attr]]) {
    override def toString = attrs.map(_.mkString(";")).mkString("\t") + "\n"
  }

  object Response {
    def from(in: InputStream) = {
      val bodyOrNull = new BufferedReader(new InputStreamReader(in)).readLine
      val body = if (bodyOrNull == null) "" else bodyOrNull
      val lines = body.split("\t").filter(!_.isEmpty).toList
      val resp = lines.map(_.split(";").toList).map(attrs => attrs.map(Attr.from(_))).toList
      Response(resp)
    }
  }

  case class Request(client: Address, attrs: List[Attr])

  object Request {
    def from(in: BufferedReader, address: Address) = {
      val bodyOrNull = in.readLine
      val body = if (bodyOrNull == null) "" else bodyOrNull
      val attrs = body.split(";").map(Attr.from(_)).toList
      new Request(address, attrs)
    }
  }

  case class Attr(key: String, value: String) {
    override def toString = key + ":" + value
  }

  object Attr {
    def from(s: String) = {
      val parts = s.split(":")
      if (parts.size == 1) Attr(parts(0), "") else Attr(parts(0), parts(1))
    }
  }
}

package spmd

object Util {
  def spawnDaemon(f: => Any) {
    val t = new Thread(new Runnable {
      def run {
        f
      }
    })
    t.setDaemon(true)
    t.start
  }
}

trait Log {
  def debug(msg: String) {
    if (System.getProperty("spmd.debug") != null) println(msg)
  }
}

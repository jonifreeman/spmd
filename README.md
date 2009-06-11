Spmd is an attempt to bring Erlang style distributed actor features to Scala.

A distributed system consists of connected *nodes*. Spmd keeps track of the nodes and
provides functions to manage the cluster. A *node* in the cluster is a JVM which is registered
to spmd process. Each node has a *name*, *network address*, and *port*. It is a responsibility
of spmd to assign a free port for node to use when it registers itself. 

Currently following features are offered (note, the scripts refered below have been tested
with Ubuntu Linux and OSX, please provide patches for other systems):

spmd
----

Port mapper daemon which keeps track of local nodes and assigns free ports to them.
spmd is started automatically when first node on host starts (unless it is already running).


    $ ./bin/spmd -help

    -kill    - shutdown running spmd
    -names   - list all nodes registered to this spmd
    -help    - this help

Nodes
-----

A node is started by giving a name for it and registering it to spmd.

    $ ./bin/node -name node1

The name must be unique. A full name of a node is [given name]@[hostname]. Full name can be given 
explicitly too: `node -name 'node1@192.168.0.100'`.
Nodes can be connected by pinging them.

    $ ./bin/node -name node1
    $ ./bin/node -name node2

    scala> import spmd.NetAdm._
    scala> ping("node2@myhost")
    res0: spmd.NetAdm.PingResponse = Pong(Node(node2,myhost,8267,8465))

Nodes form a cluster transitively (each node connects to all other nodes in a cluster,
support for other topologies are not yet implemented).

    $ ./bin/node -name node3
    
    scala> ping("node3@myhost")
    res1: spmd.NetAdm.PingResponse = Pong(Node(node3,myhost,8345,8389))

    scala> nodes        
    res2: List[spmd.Node] = List(Node(node2,myhost,8267,8465), Node(node3,myhost,8345,8389))

Function `spmd.NetAdm.nodes` lists all other nodes in the cluster, function
`spmd.NetAdm.node` gives local node. Therefore the whole cluster is:

    scala> node :: nodes
    res3: List[spmd.Node] = List(Node(node1,myhost,8278,8127), Node(node2,myhost,8267,8465), Node(node3,myhost,8345,8389))

If a node crashes it is automatically removed from the cluster.

Remote actors
-------------

Remote actors can be used without manually assigning ports to them (as is required
with standard library).

    import scala.actors.Actor._
    import spmd.RemoteActor._
    
    actor {
      register('myName, self)
      loop { receive { case x => println("got " + x) }}
    }

It can be accessed from a different node by selecting it in the following way: 

    import spmd.RemoteActor._

    val a = select('myName)
    a ! "hello"

Node monitoring
---------------

Node can be monitored remotely from any other node.

    scala> monitorNode("node3@myhost")
    res6: spmd.NetAdm.PingResponse = Pong(Node(node3,myhost,8345,8389))

    scala> import scala.actors.Actor._
    scala> receive { case x => println("Got message: " + x) }

    // Now, kill node 'node3'. The monitoring node will get the event.

    Got message: NodeDown(Node(node3,myhost,8345,8389))

TODO
----

- Proper global name server
- Erlang cookie like security system
- Hidden nodes
- etc. etc.

Compile
-------

    ./sbt compile


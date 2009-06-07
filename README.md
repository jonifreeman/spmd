Spmd is an attempt to bring Erlang like distributed actor features to Scala.

A distributed system consists of connected *nodes*. Spmd keeps track of the nodes and
provides functions to manage the cluster. A *node* in the cluster is a JVM which is registered
to spmd process. It is a responsibility of spmd to assign a free port for node to use when
it registers itself. Each node has a *name*, *network address*, and *port*.

Currently it offers following features (note, the scripts referred below has only been tested
with Ububtu Linux, please provide patches for other systems):

spmd
----

Port mapper daemon which keeps track of local nodes and assigns free ports for them.
spmd is started automatically when first node on host starts (unless it is already running).


    $ spmd -help

    -kill    - shutdown running spmd
    -names   - list all nodes registered to this spmd
    -help    - this help

Nodes
-----

A node is started by giving it a name and registering it to spmd.

    $ node -name node1

The name must be unique. The full name of the node is <given name>@<hostname>.
Nodes can be connected by pinging them.

    $ node -name node1
    $ node -name node2

    scala> import 
    scala> ping("node2")
    res0: spmd.NetAdm.PingResponse = Pong(Node(node2,nipert,8267,8465))

Nodes form a cluster transitively (each node connects to all other nodes in cluster,
support for other topologies are not yet implemented).

    $ node -name node3
    
    scala> ping("node3")
    res1: spmd.NetAdm.PingResponse = Pong(Node(node3,nipert,8345,8389))

    scala> nodes        
    res2: List[spmd.Node] = List(Node(node2,nipert,8267,8465), Node(node3,nipert,8345,8389))

Function `spmd.NetAdm.nodes` lists all other nodes in the cluster, function
`spmd.NetAdm.node` gives local node. Therefore the whole cluster is:

    scala> node :: nodes
    res3: List[spmd.Node] = List(Node(node1,nipert,8278,8127), Node(node2,nipert,8267,8465), Node(node3,nipert,8345,8389))

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

    scala> monitorNode("node3")
    res6: spmd.NetAdm.PingResponse = Pong(Node(node3,nipert,8345,8389))

    scala> import scala.actors.Actor._
    scala> receive { case x => println("Got message: " + x) }

    // Now, kill node 'node3'. The monitoring node will get the event.

    Got message: NodeDown(Node(node3,nipert,8345,8389))


TODO
----

- Global name server
- Erlang cookie like security system
- Hidden nodes
- etc. etc.


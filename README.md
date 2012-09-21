# CPS Wrappers for Java Asynchronous IO

The Treode CPS library wraps Java7's asynchronous sockets and files with Scala's continuations.  You
can write readable code without gnarly callbacks:

    val address = new InetSocketAddress ("0.0.0.0", 4567)
    val client = newSocket
    client.connect (address)
    val input = InputBuffer (client)
    writeString (client, "Hello")
    println (readString (input))

And let Scala's continuations plugin translate it to callbacks for you.  The CPS library glues the
continuations to the Java7 asynchronous methods.  The CPS library also offers continuations style
futures, locks and mailboxes.  It provides a scheduler to connect the continuations tasks to any
Java executor, including `ForkJoinPool`.  An finally, it offers some tools to test applications,
including a single threaded random scheduler, which deterministically simulates multithreaded
scheduling; if it reveals a race condition, then it will do so repeatably so that you can debug it.

For working client and server applications, see the [CPS Example](https://github.com/Treode/cps-example).

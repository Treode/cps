# CPS Wrappers for Java Asynchronous IO

The Treode CPS library wraps Java7's asynchronous sockets and files with Scala's continuations.  You
can write readable code without gnarly callbacks:

    val client = newSocket
    client.connect (addr)
    val input = InputBuffer (client)
    writeString (client, "Hello")
    println (readString (input))

And let Scala's continuations plugin translate it to callbacks for you.  The CPS library glues the
continuations to the Java7 asynchronous methods.

The CPS library also offers continuations style futures, locks and mailboxes.  Finally, it provides
a scheduler to connect the continuations tasks to any Java executor, including `ForkJoinPool`.

For working client and server applications, see the [CPS Example](https://github.com/Treode/cps-example).

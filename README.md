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

To use the library, and to include the testing stubs in your tests only, add the Treode OSS
repository and add CPS as a dependency:

    resolvers += Resolver.url (
      "treode-oss-releases",
      new URL ("http://treode.artifactoryonline.com/treode/oss-releases")) (Resolver.ivyStylePatterns)

    libraryDependencies += "com.treode" %% "cps" % "0.2.0" % "compile;test->scalatest"

Browse the [Scaladoc online](http://treode.github.com/cps/).

Browse or subscribe to the [online forum](https://groups.google.com/forum/#!forum/scala-cps-io).

For working client and server applications, see the
[CPS Example](https://github.com/Treode/cps-example).

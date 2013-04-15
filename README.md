# CPS Wrappers for Java Asynchronous IO

The Treode CPS IO library wraps Java7's asynchronous sockets and files with Scala's continuations.
You can write readable code without gnarly callbacks:

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
      "treode-oss",
      new URL ("https://oss.treode.com/ivy")) (Resolver.ivyStylePatterns)

    libraryDependencies += "com.treode" %% "cps" % "0.4.0" % "compile;test->scalatest"

You also need to setup SBT with the continuations plugin and activate it:

    addCompilerPlugin ("org.scala-lang.plugins" % "continuations" % "2.9.3"),
    scalacOptions ++= Seq ("-P:continuations:enable"),

Unfortunately, the CPS IO library cannot be used in Scala 2.10.x due to [Bug
6817](https://issues.scala-lang.org/browse/SI-6817).

Browse the [Scaladoc online](http://oss.treode.com/docs/scala/cps/0.4.0).

Browse or subscribe to the [online forum](https://groups.google.com/forum/#!forum/scala-cps-io).

For working client and server applications, see the [CPS IO
Example](https://github.com/Treode/cps-example).

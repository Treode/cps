package com.treode.cps

/** The scalatest package contains tools to support testing with Scalatest 2.0.  The scalatest
  * Scala package lives in the scalatest Ivy package.  To make the utilities available to your
  * tests, add the CPS dependency in SBT as follows:
  *
  * '''
  * libraryDependencies += "com.treode" %% "cps" % "0.2.0" % "compile;test->scalatest"
  * '''
  *
  * The scalatest Ivy package implicitly pulls in the stub Ivy package, so the above line also
  * makes the CPS stubs available to your tests.
  */
package object scalatest {}

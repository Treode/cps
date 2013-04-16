# CPS IO, Release Notes

## 0.4.0
**Apr 15, 2013**

- **Moved the Ivy repository.**  It is now at http://oss.treode.com/ivy.

- The NewRelic Java agent trips on Scala methods that return a Byte.  Added a workaround to the
  buffer and stream classes.

- Made various bug fixes.

## 0.3.0
**Jan 14, 2013**

- **Changed the API in breaking ways.** Made the scheduler an implicit parameter to classes that
  need it.  This API change is not backwards compatible.  No one depends on CPS IO yet, so we expect
  the change to be okay.

- Simplified the AtomicState class, which was overmisengineered.

- Made various bug fixes.

## 0.2.0
**Sep 29, 2012**

- **Changed the API in breaking ways.** Moved stubs into a Scala package called stub to clearly
  identify classes that are only available from the Ivy package call stub.  Moved scalatest tools
  into a Scala package called scalatest for a similar reason.  No one depends on CPS IO yet, so we
  expect the change to be okay.

## 0.1.0
**Sep 28, 2012**

- Initial release.
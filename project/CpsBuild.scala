/* Copyright (C) 2012-2013 Treode, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import Keys._

object CpsBuild extends Build {

  private def publishArtifacts (config: Configuration, tasks: TaskKey[File]*) =
    tasks map (t => addArtifact (artifact in (config, t), t in config)) flatten

  // Add scalatest and stub configs.
  lazy val Stub = config ("stub") extend (Compile)
  lazy val ScalaTest = config ("scalatest") extend (Stub)
  lazy val Test = config ("test") extend (ScalaTest)

  lazy val cpsSettings =
    inConfig (Stub) (Defaults.configSettings) ++
    inConfig (ScalaTest) (Defaults.configSettings) ++
    inConfig (Test) (Defaults.testSettings) ++
    publishArtifacts (Stub, packageBin, packageDoc, packageSrc) ++
    publishArtifacts (ScalaTest, packageBin, packageDoc, packageSrc) ++
  Seq (
    organization := "com.treode",
    name := "cps",
    version := "0.5.0-SNAPSHOT",
    scalaVersion := "2.9.3",

    addCompilerPlugin ("org.scala-lang.plugins" % "continuations" % "2.9.3"),
    scalacOptions ++= Seq ("-P:continuations:enable", "-deprecation"),

    libraryDependencies ++= Seq (
      "org.scalatest" %% "scalatest" % "2.0.M5b" % "scalatest;test",
      "org.scalacheck" %% "scalacheck" % "1.10.1" % "scalatest;test"),

    publishArtifact in Stub := true,
    publishArtifact in ScalaTest := true,
    publishArtifact in Test := false,
    publishMavenStyle := false)

  lazy val root = Project ("root", file ("."))
    .configs (Stub, ScalaTest, Test)
    .settings (cpsSettings: _*)

}

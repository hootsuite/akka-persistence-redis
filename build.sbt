import sbt._
import Version._
import Settings._

name := "akka-persistence-redis"

organization := "com.hootsuite"

version := Version.project

scalaVersion := Version.scala

crossScalaVersions := Version.crossScala

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-contrib" % Version.akka,
  "com.github.etaty"     %% "rediscala" % Version.rediscala,
  "io.spray"                %% "spray-json" % Version.sprayJson,
  "net.databinder.dispatch" %% "dispatch-core" % Version.dispatch
)

// Test dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-persistence-tck" % Version.akka % "test"
)

Settings.publishSettings

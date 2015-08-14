import Version._

name := "akka-persistence-redis"

organization := "com.hootsuite"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-contrib" % Version.akka,
  "com.etaty.rediscala"  %% "rediscala" % Version.rediscala,
  "com.typesafe.play"    %% "play-json" % Version.play,
  "commons-codec"        %  "commons-codec"  % "1.9"
)

// Test dependencies
libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-persistence-tck-experimental" % Version.akka % "test"
)

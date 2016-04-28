#Akka Persistence Redis Plugin

## What is Akka Persistence Redis plugin?
This is a plugin for Akka Persistence that uses Redis as backend. It uses [rediscala](https://github.com/etaty/rediscala), an asynchronous Redis client written with Akka and Scala.
It also depends on play-json for JSON serialization.
 
## Compatibility
### Akka 2.4.x and Play 2.5.x
Use versions from *0.4.0* for Akka 2.4.x and Play 2.5.x
```
resolvers += Resolver.jcenterRepo // Adds Bintray to resolvers for akka-persistence-redis and rediscala
libraryDependencies ++= Seq("com.hootsuite" %% "akka-persistence-redis" % "0.4.0")
```

### Akka 2.4.x and Play 2.4.x
Use versions from *0.3.0* for Akka 2.4.x and Play 2.4.x
```
resolvers += Resolver.jcenterRepo // Adds Bintray to resolvers for akka-persistence-redis and rediscala
libraryDependencies ++= Seq("com.hootsuite" %% "akka-persistence-redis" % "0.3.0")
```

### Akka 2.3.x
Use versions up to *0.2.2* for Akka 2.3.x
```
resolvers += Resolver.jcenterRepo // Adds Bintray to resolvers for akka-persistence-redis and rediscala
libraryDependencies ++= Seq("com.hootsuite" %% "akka-persistence-redis" % "0.2.2")
```
Deprecated methods since Akka 2.3.4 are NOT implemented. As a result, some tests in [TCK](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html#Plugin_TCK) fail.
Rest of methods are tested with the test harness included in this project.
  
### SNAPSHOT
Development snapshots are published on JFrog OSS 
`resolvers += "akka-persistence-redis" at "http://oss.jfrog.org/oss-snapshot-local"`

## Quick start guide
### Installation
build.sbt
```
resolvers += Resolver.jcenterRepo // Adds Bintray to resolvers for akka-persistence-redis and rediscala
libraryDependencies ++= Seq("com.hootsuite" %% "akka-persistence-redis" % "0.4.0")
```
### Activation
```
akka.persistence.journal.plugin = "akka-persistence-redis.journal"
akka.persistence.snapshot-store.plugin = "akka-persistence-redis.snapshot"
```
### Redis config
From [rediscala](https://github.com/etaty/rediscala)
```
redis {
  host = "localhost"
  port = 6379
}
```

## Usage
### Redis keys
Journal and snapshot use "journal:" and "snapshot:" as part of keys respectively as it is a good practice to create namespace for keys [reference](https://redislabs.com/blog/5-key-takeaways-for-developing-with-redis)
  
### How is data stored in Redis?
In order to enforce ordering, journal entries and snapshots are inserted into [Sorted Set](http://redis.io/commands#sorted_set) and sorted by sequenceNr
 
### Using custom ExecutionContext
By default, global ExecutionContext is used for Redis operation. This blocks calling thread for synchronous Akka Persistence APIs.
Override JournalExecutionContext trait to use custom thread pool if blocking in global is undesirable.
```
trait CustomExecutionContext extends JournalExecutionContext {
  override implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
}
```

## How to contribute
Contribute by submitting a PR and a bug report in GitHub. 

## Maintainer
[Steve Song](https://github.com/ssong-van) [@ssongvan](https://twitter.com/ssongvan)

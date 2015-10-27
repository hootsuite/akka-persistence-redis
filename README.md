#Akka Persistence Redis Plugin

## What is Akka Persistence Redis plugin?
This is a plugin for Akka Persistence that uses Redis as backend.
 
## Compatibility
This plugin was developed for Akka 2.3.x and Scala 2.11.x
It uses [rediscala](https://github.com/etaty/rediscala), an asynchronous Redis client written with Akka and Scala.
Deprecated methods since Akka 2.3.4 are NOT implemented. As a result, some tests in [TCK](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html#Plugin_TCK) fail.
Rest of methods are tested with the test harness included in this project.  

## Quick start guide
### Installation
plugins.sbt
```
resolvers += Resolver.jcenterRepo // Adds Bintray to resolvers for akka-persistence-redis and rediscala
```
build.sbt
```
libraryDependencies ++= Seq("com.hootsuite" %% "akka-persistence-redis" % "0.1.0")
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

# Notable changes for each version increment

# Development SNAPSHOT
Nothing yet

# 0.8.0
- Retry Redis operation when the server connection is lost
    - Only retries on NoConnectionException
    - Retry performed with exponential backoff
    - Separate configuration for read, write, and delete
    - Original exception is thrown to Akka if all retry attemps failed

# 0.7.2
- Fallback to `fromSequenceNr` instead of to `0L` in Journal::asyncReadHighestSequenceNr

# 0.7.1
- Added `redis.db` config to select DB 

# 0.7.0
- Scala 2.12 compatibility
    - Replace play-json with spray-json
    - Upgrade akka & rediscala
    - Drop unused commons-codec dependency

# 0.6.0
- Support for Microsoft Azure Redis. Override the number of maximum number of messages to replay because the default is too big for Azure Redis.
- Option to override key namespace with `journal.key-namespace` and `snapshot.key-namespace`

# 0.5.0
- Support for Redis Sentinel

# 0.4.0
- Pull in Play 2.5 for play-json. Fixes issues when the plugin is used with Play 2.5

# 0.3.0
- Support for Akka 2.4

# 0.2.2
- Release on the official Hootsuite Bintray account

# 0.2.1
- Add resolver for jcenter in build.sbt

# 0.2.0
- Bump rediscala version to 1.5.0
- efc213 configure Redis from same config as the actor system

# 0.1.0
First stable version

# 0.0.3
Test release for Bintray

# 0.0.2
Test release for Bintray

# 0.0.1
Initial commit

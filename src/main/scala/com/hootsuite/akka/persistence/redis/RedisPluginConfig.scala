package com.hootsuite.akka.persistence.redis

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration._

case class RetryConfig(max: Int, delay: FiniteDuration, base: Int) {
  def this(config: Config, prefix: String) =
    this(
      config.getInt(s"$prefix.max"),
      config.getDuration(s"$prefix.delay", TimeUnit.MILLISECONDS).millis,
      config.getInt(s"$prefix.base")
    )
}

class RedisPluginRetryConfig(config: Config) {
  val writeRetryConfig = new RetryConfig(config, "akka-persistence-redis.write-retries")
  val readRetryConfig = new RetryConfig(config, "akka-persistence-redis.read-retries")
  val deleteRetryConfig = new RetryConfig(config, "akka-persistence-redis.delete-retries")
}


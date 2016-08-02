package com.hootsuite.akka.persistence.redis

import akka.actor.ActorSystem
import redis.RedisClient


trait DefaultRedisComponent {
  implicit val actorSystem: ActorSystem

  private val config = actorSystem.settings.config
  private val sentinel = config.getBoolean("redis.sentinel")

  lazy val redis = if(sentinel){
    SentinelUtils.getSentinelBasedClient(config)
  } else {
    val host = config.getString("redis.host")
    val port = config.getInt("redis.port")
    RedisClient(host, port)
  }

}
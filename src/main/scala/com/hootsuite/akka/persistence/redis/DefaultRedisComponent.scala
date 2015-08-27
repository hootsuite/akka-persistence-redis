package com.hootsuite.akka.persistence.redis

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import redis.RedisClient

trait DefaultRedisComponent {
  implicit val actorSystem: ActorSystem

  private val config = ConfigFactory.load()
  private val host = config.getString("redis.host")
  private val port = config.getInt("redis.port")

  val redis = new RedisClient(host, port)
}
package com.hootsuite.akka.persistence.redis

import akka.actor.ActorSystem
import com.typesafe.config.Config
import redis.{SentinelMonitoredRedisClient, RedisClient}
import scala.collection.JavaConversions._

object SentinelUtils {

  def getSentinelBasedClient(config: Config)(implicit system:ActorSystem):RedisClient = {

    val sentinelMaster = config.getString("redis.sentinel-master")
    val sentinels = config.getConfigList("redis.sentinels") map { conf =>
      conf.getString("host") -> conf.getInt("port")
    }
    val dbConfig = "redis.db"
    val db = config.hasPath(dbConfig) match {
      case true => Some(config.getInt(dbConfig))
      case false => None
    }
    SentinelMonitoredRedisClient(sentinels, sentinelMaster, db = db).redisClient
  }

}

package com.hootsuite.akka.persistence.redis

import akka.actor.ActorSystem
import dispatch.retry
import dispatch.Defaults._
import redis.RedisClient

import scala.concurrent.Future


trait DefaultRedisComponent {
  implicit val actorSystem: ActorSystem

  private val config = actorSystem.settings.config
  private val sentinel = config.getBoolean("redis.sentinel")

  /**
    * Generic plugin configuration
    */
  protected val pluginRetryConfig = new RedisPluginRetryConfig(config)

  lazy val redis = if(sentinel){
    SentinelUtils.getSentinelBasedClient(config)
  } else {
    val host = config.getString("redis.host")
    val port = config.getInt("redis.port")
    val passwordConfig = "redis.password"
    val password = config.hasPath(passwordConfig) match {
      case true => Some(config.getString(passwordConfig))
      case false => None
    }
    val dbConfig = "redis.db"
    val db = config.hasPath(dbConfig) match {
      case true => Some(config.getInt(dbConfig))
      case false => None
    }
    RedisClient(host, port, password = password, db = db)
  }

  def backOffWithConfig[T](retryConfig: RetryConfig)
                          (promise: () => Future[Either[Throwable,T]]): Future[T] = {
    retry.Backoff(
      max = retryConfig.max,
      delay = retryConfig.delay,
      base = retryConfig.base)(promise).map {
      case Left(ex) =>
        throw ex
      case Right(response) =>
        response
    }
  }
}
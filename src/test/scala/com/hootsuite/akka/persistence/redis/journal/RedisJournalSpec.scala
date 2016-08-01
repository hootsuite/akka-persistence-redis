package com.hootsuite.akka.persistence.redis.journal

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import com.hootsuite.akka.persistence.redis.SentinelUtils
import com.typesafe.config.ConfigFactory
import redis.RedisClient

/**
 * Akka Persistence Journal TCK tests provided by Akka
 */
class RedisJournalSpec extends JournalSpec(
  config = ConfigFactory.parseString(
    """
      |akka.persistence.journal.plugin = "akka-persistence-redis.journal"
      |akka-persistence-redis.journal.class = "com.hootsuite.akka.persistence.redis.journal.RedisJournal"
    """.stripMargin)
) {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  override def beforeAll() = {
    super.beforeAll()
    val config = ConfigFactory.load()

    val sentinel = config.getBoolean("redis.sentinel")

    lazy val redis = if(sentinel){
      SentinelUtils.getSentinelBasedClient(config)
    } else {
      val host = config.getString("redis.host")
      val port = config.getInt("redis.port")
      new RedisClient(host, port)
    }

    // Clean up database to prevent data from previous tests interfering with current run
    redis.flushall()
  }
}
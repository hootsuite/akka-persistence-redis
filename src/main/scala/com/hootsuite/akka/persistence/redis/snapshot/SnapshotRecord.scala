package com.hootsuite.akka.persistence.redis.snapshot

import akka.util.ByteString
import com.hootsuite.akka.persistence.redis.SerializationException
import spray.json._
import redis.ByteStringFormatter

/**
 * Snapshot entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */
case class SnapshotRecord(sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])

object SnapshotRecord extends DefaultJsonProtocol {
  implicit val fmt = jsonFormat3(SnapshotRecord.apply)

  implicit val byteStringFormatter = new ByteStringFormatter[SnapshotRecord] {
    override def serialize(data: SnapshotRecord): ByteString = {
      ByteString(data.toJson.toString)
    }

    override def deserialize(bs: ByteString): SnapshotRecord = {
      try {
        bs.utf8String.parseJson.convertTo[SnapshotRecord]
      } catch {
        case e: Exception => throw SerializationException("Error deserializing SnapshotRecord.", e)
      }
    }
  }
}

package com.hootsuite.akka.persistence.redis.journal

import akka.util.ByteString
import com.hootsuite.akka.persistence.redis.SerializationException
import spray.json._
import redis.ByteStringFormatter

/**
 * Journal entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */
case class Journal(sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean)

object Journal extends DefaultJsonProtocol {
  implicit val fmt = jsonFormat3(Journal.apply)

  implicit val byteStringFormatter = new ByteStringFormatter[Journal] {
    override def serialize(data: Journal): ByteString = {
      ByteString(data.toJson.toString)
    }

    override def deserialize(bs: ByteString): Journal = {
      try {
        bs.utf8String.parseJson.convertTo[Journal]
      } catch {
        case e: Exception => throw SerializationException("Error deserializing Journal.", e)
      }
    }
  }
}

package com.hootsuite.akka.persistence.redis.snapshot

import akka.util.ByteString
import com.hootsuite.akka.persistence.redis.SerializationException
import org.apache.commons.codec.binary.Base64
import play.api.libs.json._
import redis.ByteStringFormatter

/**
 * Snapshot entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */
case class SnapshotRecord(sequenceNr: Long, timestamp: Long, snapshot: Array[Byte])

object SnapshotRecord {
  implicit val byteArrayWrites = new Writes[Array[Byte]] {
    override def writes(ba: Array[Byte]): JsValue = {
      JsString(Base64.encodeBase64String(ba))
    }
  }

  implicit val byteArrayReads = new Reads[Array[Byte]] {
    override def reads(json: JsValue): JsResult[Array[Byte]] = json match {
      case JsString(s) =>
        try {
          JsSuccess(Base64.decodeBase64(s))
        } catch {
          case _: Throwable => JsError("Cannot deserialize snapshot in SnapshotRecord")
        }
      case _ => JsError("Cannot find deserializable JsValue")
    }
  }

  implicit val fmt: Format[SnapshotRecord] = Json.format[SnapshotRecord]

  implicit val byteStringFormatter = new ByteStringFormatter[SnapshotRecord] {
    override def serialize(data: SnapshotRecord): ByteString = {
      ByteString(Json.toJson(data).toString())
    }

    override def deserialize(bs: ByteString): SnapshotRecord = {
      try {
        Json.parse(bs.utf8String).as[SnapshotRecord]
      } catch {
        case e: Exception => throw SerializationException("Error deserializing SnapshotRecord.", e)
      }
    }
  }
}
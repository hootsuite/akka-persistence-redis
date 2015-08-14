package com.hootsuite.akka.persistence.redis.journal

import akka.util.ByteString
import com.hootsuite.akka.persistence.redis.SerializationException
import org.apache.commons.codec.binary.Base64
import play.api.libs.json._
import redis.ByteStringFormatter

/**
 * Journal entry that can be serialized and deserialized to JSON
 * JSON in turn is serialized to ByteString so it can be stored in Redis with Rediscala
 */
case class Journal(sequenceNr: Long, persistentRepr: Array[Byte], deleted: Boolean)

object Journal {
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
          case _: Throwable => JsError("Cannot deserialize persistentRepr in Journal")
        }
      case _ => JsError("Cannot find deserializable JsValue")
    }
  }

  implicit val fmt: Format[Journal] = Json.format[Journal]

  implicit val byteStringFormatter = new ByteStringFormatter[Journal] {
    override def serialize(data: Journal): ByteString = {
      ByteString(Json.toJson(data).toString())
    }

    override def deserialize(bs: ByteString): Journal = {
      try {
        Json.parse(bs.utf8String).as[Journal]
      } catch {
        case e: Exception => throw SerializationException("Error deserializing Journal.", e)
      }
    }
  }
}

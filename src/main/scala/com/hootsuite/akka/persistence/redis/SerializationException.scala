package com.hootsuite.akka.persistence.redis

case class SerializationException(message: String, cause: Throwable) extends Throwable(message, cause)

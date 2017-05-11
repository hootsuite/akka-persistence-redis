package com.hootsuite.akka.persistence.redis.journal

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.journal.AsyncWriteJournal
import com.hootsuite.akka.persistence.redis.{ByteArraySerializer, DefaultRedisComponent}
import redis.actors.NoConnectionException
import redis.api.Limit
import redis.commands.TransactionBuilder

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Writes journals in Sorted Set, using SequenceNr as score.
 * Deprecated API's are not implemented which causes few TCK tests to fail.
 */
class RedisJournal extends AsyncWriteJournal with ActorLogging with DefaultRedisComponent with ByteArraySerializer with JournalExecutionContext {

  /**
   * Define actor system for Rediscala and ByteArraySerializer
   */
  override implicit lazy val actorSystem = context.system

  private val config = actorSystem.settings.config

  /**
   * Get the journal key namespace from config
   */
  private val journalKeyNamespace = config.getString("akka-persistence-redis.journal.key-namespace")

  /**
   * Read the max-replay-messages in the config, to be used when replaying the messages (since some Redis
   * implementation does not support huge values like Microsoft Azure Redis)
   */
  private val maxReplayMessagesConfig = "akka-persistence-redis.journal.max-replay-messages"
  private val maxReplayMessages = config.hasPath(maxReplayMessagesConfig) match {
    case true => Some(config.getLong(maxReplayMessagesConfig))
    case false => None
  }
  // Redis key namespace for journals
  private def journalKey(persistenceId: String) = s"$journalKeyNamespace:$persistenceId"

  private def highestSequenceNrKey(persistenceId: String) = s"${journalKey(persistenceId)}.highestSequenceNr"

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    Future.sequence(messages.map(asyncWriteBatch))
  }

  private def asyncWriteBatch(a: AtomicWrite): Future[Try[Unit]] = {
    val getResultFuture = () => {
      val transaction = redis.transaction()

      val batchOperations = Future
        .sequence {
          a.payload.map(asyncWriteOperation(transaction, _))
        }
        .map(_ => ())

      transaction.exec()
      batchOperations
        .map(u => Right(Success(u)))
        .recover {
          case ex@NoConnectionException => Left(ex) // potential retry in that case
          case ex => Right(Failure(ex))
        }
    }
    backOffWithConfig(pluginRetryConfig.writeRetryConfig)(getResultFuture)
  }

  private def asyncWriteOperation(transaction: TransactionBuilder, pr: PersistentRepr): Future[Unit] = {
    import Journal._

    toBytes(pr) match {
      case Success(serialized) =>
        val journal = Journal(pr.sequenceNr, serialized, pr.deleted)
        transaction.zadd(journalKey(pr.persistenceId), (pr.sequenceNr, journal)).zip(
          transaction.set(highestSequenceNrKey(pr.persistenceId), pr.sequenceNr)
        ).map(_ => ())
      case Failure(e) => Future.failed(new scala.RuntimeException("writeMessages: failed to write PersistentRepr to redis"))
    }
  }

  /**
   * Plugin API: asynchronously deletes all persistent messages up to `toSequenceNr`
   * (inclusive).
   *
   * This call is protected with a circuit-breaker.
   * Message deletion doesn't affect the highest sequence number of messages, journal must maintain the highest sequence number and never decrease it.
   */
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {

    val getResultFuture = () => {
      redis.zremrangebyscore(journalKey(persistenceId), Limit(-1), Limit(toSequenceNr)).map { _ => Right(Success()) }
    }.recover {
      case ex@NoConnectionException => Left(ex) // potential retry in that case
      case ex => Right(Failure(ex))
    }
    backOffWithConfig(pluginRetryConfig.deleteRetryConfig)(getResultFuture).map { case u => u.get }
  }

  /**
   * Plugin API: asynchronously replays persistent messages. Implementations replay
   * a message by calling `replayCallback`. The returned future must be completed
   * when all messages (matching the sequence number bounds) have been replayed.
   * The future must be completed with a failure if any of the persistent messages
   * could not be replayed.
   *
   * The `replayCallback` must also be called with messages that have been marked
   * as deleted. In this case a replayed message's `deleted` method must return
   * `true`.
   *
   * The channel ids of delivery confirmations that are available for a replayed
   * message must be contained in that message's `confirms` sequence.
   *
   * @param persistenceId persistent actor id.
   * @param fromSequenceNr sequence number where replay should start (inclusive).
   * @param toSequenceNr sequence number where replay should end (inclusive).
   * @param max maximum number of messages to be replayed.
   * @param replayCallback called to replay a single message. Can be called from any
   *                       thread.
   *
   */
  def asyncReplayMessages(persistenceId : String, fromSequenceNr : Long, toSequenceNr : Long, max : Long)
    (replayCallback : PersistentRepr => Unit) : Future[Unit] = {

    import Journal._

    val getResultFuture = () => {
      for {
        journals <- redis.zrangebyscore(journalKey(persistenceId), Limit(fromSequenceNr), Limit(toSequenceNr), Some((0L,
          maxReplayMessages match {
            case Some(m) if max > m => m
            case _ => max
          })))
      } yield {
        journals.foreach { journal =>
          fromBytes[PersistentRepr](journal.persistentRepr) match {
            case Success(pr) => replayCallback(pr)
            case Failure(_) => Future.failed(throw new RuntimeException("asyncReplayMessages: Failed to deserialize PersistentRepr"))
          }
        }
        Right(Success())
      }
      }.recover {
        case ex@NoConnectionException => Left(ex) // potential retry in that case
        case ex => Right(Failure(ex))
      }

    backOffWithConfig(pluginRetryConfig.readRetryConfig)(getResultFuture).map { case u => u.get }
  }

  /**
   * Plugin API: asynchronously reads the highest stored sequence number for the
   * given `persistenceId`.
   *
   * @param persistenceId persistent actor id.
   * @param fromSequenceNr hint where to start searching for the highest sequence
   *                       number.
   */
  def asyncReadHighestSequenceNr(persistenceId : String, fromSequenceNr : Long) : Future[Long] = {
    val getResultFuture = () => {
      redis.get(highestSequenceNrKey(persistenceId)).map {
        highestSequenceNr => Right(Success(highestSequenceNr.map(_.utf8String.toLong).getOrElse(fromSequenceNr)))
      }.recover {
        case ex@NoConnectionException => Left(ex) // potential retry in that case
        case ex => Right(Failure(ex))
        }
    }
    backOffWithConfig(pluginRetryConfig.readRetryConfig)(getResultFuture).map { case u => u.get }
  }
}

trait JournalExecutionContext {
  // Global ExecutionContext is provided to Rediscala for non-blocking asynchronous Redis operations.
  // Be cautioned that it can be blocked for synchronous APIs.
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val ec: ExecutionContext = global
}

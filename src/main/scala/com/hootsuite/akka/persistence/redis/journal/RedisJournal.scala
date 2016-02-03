package com.hootsuite.akka.persistence.redis.journal

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.journal.{AsyncWriteJournal}
import com.hootsuite.akka.persistence.redis.{ByteArraySerializer, DefaultRedisComponent}
import redis.api.Limit

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Try, Failure, Success}

/**
 * Writes journals in Sorted Set, using SequenceNr as score.
 * Deprecated API's are not implemented which causes few TCK tests to fail.
 */
class RedisJournal extends AsyncWriteJournal with ActorLogging with DefaultRedisComponent with ByteArraySerializer with JournalExecutionContext {

  /**
   * Define actor system for Rediscala and ByteArraySerializer
   */
  override implicit lazy val actorSystem = context.system

  // Redis key namespace for journals
  private def journalKey(persistenceId: String) = s"journal:$persistenceId"


  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = Future.fromTry(Try {
    messages.map { a =>
      Try {
        writeMessages(a.payload)
      }
    }
  })

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = try Future.successful {
    deleteMessagesTo(persistenceId, toSequenceNr, true)
  } catch {
    case NonFatal(e) => Future.failed(e)
  }

  /**
   * Plugin API: synchronously writes a batch of persistent messages to the journal.
   * The batch write must be atomic i.e. either all persistent messages in the batch
   * are written or none.
   */
  def writeMessages(messages: Seq[PersistentRepr]): Unit = {

    import Journal._

    val transaction = redis.transaction()

    messages.map { pr =>
      toBytes(pr) match {
        case Success(serialized) =>
          val journal = Journal(pr.sequenceNr, serialized, pr.deleted)
          transaction.zadd(journalKey(pr.persistenceId), (pr.sequenceNr, journal))
        case Failure(e) => Future.failed(throw new RuntimeException("writeMessages: failed to write PersistentRepr to redis"))
      }
    }

    Await.result(transaction.exec(), 1 second)
  }

  /**
   * Plugin API: synchronously deletes all persistent messages up to `toSequenceNr`
   * (inclusive). If `permanent` is set to `false`, the persistent messages are marked
   * as deleted, otherwise they are permanently deleted.
   */
  def deleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Unit = {

    import Journal._

    val f = permanent match {
      case true => redis.zremrangebyscore(journalKey(persistenceId), Limit(-1), Limit(toSequenceNr))
      case false =>
        for {
          journals <- redis.zrangebyscore(journalKey(persistenceId), Limit(-1), Limit(toSequenceNr))
        } yield {
          val transaction = redis.transaction()
          journals.foreach { journal =>
            val pr = fromBytes[PersistentRepr](journal.persistentRepr).toOption.get
            val newPr = pr.update(deleted = true)
            toBytes(newPr) match {
              case Success(serialized) =>
                transaction.zremrangebyscore(journalKey(pr.persistenceId), Limit(pr.sequenceNr), Limit(pr.sequenceNr))
                transaction.zadd(journalKey(newPr.persistenceId), (newPr.sequenceNr, Journal(newPr.sequenceNr, serialized, newPr.deleted)))
              case Failure(e) => throw new RuntimeException("deleteMessagesTo: failed to deserialize journal entry to delete")
            }
          }

          transaction.exec()
        }
    }

    Await.result(f, 1 second)
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

    for {
      journals <- redis.zrangebyscore(journalKey(persistenceId), Limit(fromSequenceNr), Limit(toSequenceNr), Some((0L, max)))
    } yield {
      journals.foreach { journal =>
        fromBytes[PersistentRepr](journal.persistentRepr) match {
          case Success(pr) => replayCallback(pr)
          case Failure(e) => Future.failed(throw new RuntimeException("asyncReplayMessages: Failed to deserialize PersistentRepr"))
        }
      }
    }
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
    import Journal._

    redis.zrevrangebyscoreWithscores(journalKey(persistenceId), Limit(Double.MaxValue), Limit(fromSequenceNr), Some(0L, 1L)).map{
      journals => journals.headOption.map{ a => a._2.toLong }.getOrElse(0L)
    }
  }
}

trait JournalExecutionContext {
  // Global ExecutionContext is provided to Rediscala for non-blocking asynchronous Redis operations.
  // Be cautioned that it can be blocked for synchronous APIs.
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val ec: ExecutionContext = global
}

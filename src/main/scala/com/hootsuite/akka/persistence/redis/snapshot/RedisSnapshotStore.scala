package com.hootsuite.akka.persistence.redis.snapshot

import akka.actor.ActorLogging
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.hootsuite.akka.persistence.redis.{ByteArraySerializer, DefaultRedisComponent}
import redis.actors.NoConnectionException
import redis.api.Limit

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Writes snapshot in Sorted Set, using SequenceNr as score.
 */
class RedisSnapshotStore extends SnapshotStore with ActorLogging with DefaultRedisComponent with ByteArraySerializer with SnapshotExecutionContext {

  /**
   * Define actor system for Rediscala and ByteArraySerializer
   */
  override implicit lazy val actorSystem = context.system

  private val config = actorSystem.settings.config

  private val snapshotKeyNamespace =config.getString("akka-persistence-redis.snapshot.key-namespace")

  // Redis key namespace for snapshots
  private def snapshotKey(persistenceId: String) = s"$snapshotKeyNamespace:$persistenceId"

  /**
   * Plugin API: asynchronously loads a snapshot.
   *
   * @param persistenceId processor id.
   * @param criteria selection criteria for loading.
   */
  def loadAsync(persistenceId : String, criteria : SnapshotSelectionCriteria) : Future[Option[SelectedSnapshot]] = {
    import SnapshotRecord._
    val getResultFuture = () => {
      val maybeSnapshot = for {
        snapshots <- redis.zrevrangebyscore(snapshotKey(persistenceId), Limit(criteria.maxSequenceNr), Limit(-1))
      } yield {
        val maybeSnapshot = snapshots.find(s => s.timestamp <= criteria.maxTimestamp && s.sequenceNr <= criteria.maxSequenceNr)
        maybeSnapshot.flatMap { s =>
          fromBytes[Snapshot](s.snapshot).toOption.map { ds =>
            SelectedSnapshot(SnapshotMetadata(persistenceId, s.sequenceNr, s.timestamp), ds.data)
          }
        }
      }

      maybeSnapshot.map(u => Right(Success(u)))
        .recover {
        case ex@NoConnectionException => Left(ex) // potential retry in that case
        case ex => Right(Failure(ex))
      }
    }
    backOffWithConfig(pluginRetryConfig.readRetryConfig)(getResultFuture).map { case u => u.get }
  }

  /**
   * Plugin API: asynchronously saves a snapshot.
   *
   * @param metadata snapshot metadata.
   * @param snapshot snapshot.
   */
  def saveAsync(metadata : SnapshotMetadata, snapshot : Any) : Future[Unit] = {
    import SnapshotRecord._

    val maybeSnapshotRecord = toBytes(Snapshot(snapshot)).map { serialized =>
      SnapshotRecord(metadata.sequenceNr, metadata.timestamp, serialized)
    }

    val getResultFuture = () => {
      maybeSnapshotRecord match {
        case Success(snapshotRecord) =>
          redis.zadd(snapshotKey(metadata.persistenceId), (metadata.sequenceNr, snapshotRecord)).map { _ => Right(Success()) }.recover {
            case ex@NoConnectionException => Left(ex) // potential retry in that case
            case ex => Right(Failure(ex))
          }
        case Failure(_) =>
          Future.successful(Right(Failure(new RuntimeException(s"Failed to save snapshot. metadata: $metadata snapshot: $snapshot"))))
      }
    }
    backOffWithConfig(pluginRetryConfig.writeRetryConfig)(getResultFuture).map { case u => u.get }
  }

  /**
   * Plugin API: deletes the snapshot identified by `metadata`.
   *
   * This call is protected with a circuit-breaker.
   *
   * @param metadata snapshot metadata.
   */
  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    val getResultFuture = () => {
      redis.zremrangebyscore(snapshotKey(metadata.persistenceId), Limit(metadata.sequenceNr), Limit(metadata.sequenceNr))
        .map(_ => Right(Success()))
    }.recover {
      case ex@NoConnectionException => Left(ex) // potential retry in that case
      case ex => Right(Failure(ex))
    }
    backOffWithConfig(pluginRetryConfig.deleteRetryConfig)(getResultFuture).map { case u => u.get }
  }


  /**
   * Plugin API: deletes all snapshots matching `criteria`.
   *
   * This call is protected with a circuit-breaker.
   *
   * @param persistenceId id of the persistent actor.
   * @param criteria selection criteria for deleting.
   */
  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    import SnapshotRecord._

    val getResultFuture = () => {
      redis
        .zrevrangebyscore(snapshotKey(persistenceId), Limit(criteria.maxSequenceNr), Limit(criteria.minSequenceNr))
        .flatMap { snapshots =>
          Future.sequence {
            snapshots
              .filter(s => s.timestamp >= criteria.minTimestamp && s.timestamp <= criteria.maxTimestamp)
              .map { s =>
                redis.zremrangebyscore(snapshotKey(persistenceId), Limit(s.sequenceNr), Limit(s.sequenceNr))
              }
          }
        }
        .map(_ => Right(Success()))
    }.recover {
      case ex@NoConnectionException => Left(ex) // potential retry in that case
      case ex => Right(Failure(ex))
    }
    backOffWithConfig(pluginRetryConfig.deleteRetryConfig)(getResultFuture).map { case u => u.get }
  }
}

trait SnapshotExecutionContext {
  // Global ExecutionContext is provided to Rediscala for non-blocking asynchronous Redis operations
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val ec: ExecutionContext = global
}
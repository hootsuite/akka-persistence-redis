package com.hootsuite.akka.persistence.redis.snapshot

import akka.actor.ActorLogging
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import com.hootsuite.akka.persistence.redis.{ByteArraySerializer, DefaultRedisComponent}
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

  // Redis key namespace for snapshots
  private def snapshotKey(persistenceId: String) = s"snapshot:$persistenceId"

  /**
   * Plugin API: asynchronously loads a snapshot.
   *
   * @param persistenceId processor id.
   * @param criteria selection criteria for loading.
   */
  def loadAsync(persistenceId : String, criteria : SnapshotSelectionCriteria) : Future[Option[SelectedSnapshot]] = {
    import SnapshotRecord._

    for {
      snapshots <- redis.zrevrangebyscore(snapshotKey(persistenceId), Limit(criteria.maxSequenceNr), Limit(-1))
    } yield {
      val maybeSnapshot = snapshots.find(s => s.timestamp <= criteria.maxTimestamp && s.sequenceNr <= criteria.maxSequenceNr)
      maybeSnapshot.flatMap { s =>
        fromBytes[Snapshot](s.snapshot).toOption.map { ds =>
          SelectedSnapshot(SnapshotMetadata(persistenceId, s.sequenceNr, s.timestamp), ds.data)
        }
      }
    }
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

    maybeSnapshotRecord match {
      case Success(snapshotRecord) =>
        redis.zadd(snapshotKey(metadata.persistenceId), (metadata.sequenceNr, snapshotRecord)).map{_ => ()}
      case Failure(e) => Future.failed(throw new RuntimeException(s"Failed to save snapshot. metadata: $metadata snapshot: $snapshot"))
    }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    Future {
      delete(metadata)
    }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    Future {
      delete(persistenceId, criteria)
    }

  /**
   * Plugin API: called after successful saving of a snapshot.
   *
   * @param metadata snapshot metadata.
   */
  def saved(metadata : SnapshotMetadata) : Unit = {}

  /**
   * Plugin API: deletes the snapshot identified by `metadata`.
   *
   * @param metadata snapshot metadata.
   */
  def delete(metadata : SnapshotMetadata): Unit = {
    redis.zremrangebyscore(snapshotKey(metadata.persistenceId), Limit(metadata.sequenceNr), Limit(metadata.sequenceNr))
  }

  /**
   * Plugin API: deletes all snapshots matching `criteria`.
   *
   * @param persistenceId processor id.
   * @param criteria selection criteria for deleting.
   */
  def delete(persistenceId : String, criteria : SnapshotSelectionCriteria): Unit = {
    redis.zremrangebyscore(snapshotKey(persistenceId), Limit(-1L), Limit(criteria.maxSequenceNr))
  }

}

trait SnapshotExecutionContext {
  // Global ExecutionContext is provided to Rediscala for non-blocking asynchronous Redis operations
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val ec: ExecutionContext = global
}
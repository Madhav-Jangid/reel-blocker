package com.reellock.reellock.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentConfigDao {
    @Insert
    suspend fun insert(config: CommitmentConfigEntity): Long

    @Query("SELECT * FROM commitment_config WHERE version = :version")
    suspend fun getByVersion(version: Int): CommitmentConfigEntity?

    @Query("SELECT * FROM commitment_config WHERE isDraft = 1 ORDER BY version DESC LIMIT 1")
    suspend fun getLatestDraft(): CommitmentConfigEntity?

    @Query("SELECT * FROM commitment_config WHERE isDraft = 1 ORDER BY version DESC LIMIT 1")
    fun observeLatestDraft(): Flow<CommitmentConfigEntity?>

    @Update
    suspend fun update(config: CommitmentConfigEntity)
}

@Dao
interface ActiveCommitmentDao {
    @Query("SELECT * FROM active_commitment WHERE id = 0")
    suspend fun get(): ActiveCommitmentEntity?

    @Query("SELECT * FROM active_commitment WHERE id = 0")
    fun observe(): Flow<ActiveCommitmentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ActiveCommitmentEntity)
}

@Dao
interface BootSessionDao {
    @Insert
    suspend fun insert(session: BootSessionEntity): Long

    @Query("SELECT * FROM boot_session ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): BootSessionEntity?

    @Query("SELECT * FROM boot_session WHERE id = :id")
    suspend fun getById(id: Long): BootSessionEntity?
}

@Dao
interface UsageIntervalDao {
    @Insert
    suspend fun insert(interval: UsageIntervalEntity): Long

    @Update
    suspend fun update(interval: UsageIntervalEntity)

    @Query("SELECT * FROM usage_interval WHERE endElapsedRealtimeMillis IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpenInterval(): UsageIntervalEntity?

    /** Oldest-first; sufficient for a rolling 5h window without unbounded growth. */
    @Query("SELECT * FROM usage_interval WHERE id = :id")
    suspend fun getById(id: Long): UsageIntervalEntity?

    @Query(
        "SELECT ui.id AS id, ui.bootSessionId AS bootSessionId, " +
            "ui.startElapsedRealtimeMillis AS startElapsedRealtimeMillis, " +
            "ui.endElapsedRealtimeMillis AS endElapsedRealtimeMillis, " +
            "ui.lastHeartbeatElapsedRealtimeMillis AS lastHeartbeatElapsedRealtimeMillis, " +
            "ui.confidence AS confidence, ui.closedReason AS closedReason, " +
            "bs.anchorWallClockMillis AS anchorWallClockMillis, " +
            "bs.anchorElapsedRealtimeMillis AS anchorElapsedRealtimeMillis " +
            "FROM usage_interval ui " +
            "JOIN boot_session bs ON ui.bootSessionId = bs.id " +
            "WHERE (bs.anchorWallClockMillis + (ui.startElapsedRealtimeMillis - bs.anchorElapsedRealtimeMillis)) " +
            "   < :windowEndWallClockMillis " +
            "ORDER BY ui.id ASC",
    )
    suspend fun getIntervalsStartingBefore(windowEndWallClockMillis: Long): List<UsageIntervalWithAnchor>

    @Query("DELETE FROM usage_interval WHERE endElapsedRealtimeMillis IS NOT NULL AND id < (SELECT MIN(id) FROM usage_interval WHERE endElapsedRealtimeMillis IS NULL)")
    suspend fun pruneOldClosedIntervals()
}

/** Join projection used by the rolling-window scan; carries the boot anchor needed to derive wall-clock bounds. */
data class UsageIntervalWithAnchor(
    val id: Long,
    val bootSessionId: Long,
    val startElapsedRealtimeMillis: Long,
    val endElapsedRealtimeMillis: Long?,
    val lastHeartbeatElapsedRealtimeMillis: Long,
    val confidence: String,
    val closedReason: String?,
    val anchorWallClockMillis: Long,
    val anchorElapsedRealtimeMillis: Long,
)

@Dao
interface RecoveryRequestDao {
    @Insert
    suspend fun insert(request: RecoveryRequestEntity)

    @Query("SELECT * FROM recovery_request WHERE nonce = :nonce")
    suspend fun getByNonce(nonce: String): RecoveryRequestEntity?

    @Update
    suspend fun update(request: RecoveryRequestEntity)

    @Query("SELECT * FROM recovery_request ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun getLatest(): RecoveryRequestEntity?
}

@Dao
interface RecoveryApprovalDao {
    @Insert
    suspend fun insert(approval: RecoveryApprovalEntity): Long

    @Query("SELECT * FROM recovery_approval WHERE requestNonce = :nonce LIMIT 1")
    suspend fun getByRequestNonce(nonce: String): RecoveryApprovalEntity?

    @Update
    suspend fun update(approval: RecoveryApprovalEntity)
}

@Dao
interface SecurityEventDao {
    @Insert
    suspend fun insert(event: SecurityEventEntity)

    @Query("SELECT * FROM security_event ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SecurityEventEntity>

    @Query("SELECT * FROM security_event ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SecurityEventEntity>>
}

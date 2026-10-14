package com.reellock.reellock.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CommitmentConfigEntity::class,
        ActiveCommitmentEntity::class,
        BootSessionEntity::class,
        UsageIntervalEntity::class,
        RecoveryRequestEntity::class,
        RecoveryApprovalEntity::class,
        SecurityEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commitmentConfigDao(): CommitmentConfigDao
    abstract fun activeCommitmentDao(): ActiveCommitmentDao
    abstract fun bootSessionDao(): BootSessionDao
    abstract fun usageIntervalDao(): UsageIntervalDao
    abstract fun recoveryRequestDao(): RecoveryRequestDao
    abstract fun recoveryApprovalDao(): RecoveryApprovalDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reellock.db",
                ).build().also { instance = it }
            }
    }
}

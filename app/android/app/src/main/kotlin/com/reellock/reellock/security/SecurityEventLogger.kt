package com.reellock.reellock.security

import com.reellock.reellock.commitment.SecurityEventType
import com.reellock.reellock.persistence.SecurityEventDao
import com.reellock.reellock.persistence.SecurityEventEntity
import com.reellock.reellock.time.TrustedTimeManager

/** Append-only audit trail (need.md `SecurityEvent` entity / R9). Never deletes or edits rows. */
class SecurityEventLogger(
    private val dao: SecurityEventDao,
    private val trustedTimeManager: TrustedTimeManager,
) {
    suspend fun log(type: SecurityEventType, outcome: String, configVersion: Int? = null, details: String? = null) {
        dao.insert(
            SecurityEventEntity(
                type = type.name,
                timestampEpochMillis = trustedTimeManager.now().nowEpochMillis,
                configVersion = configVersion,
                outcome = outcome,
                details = details,
            ),
        )
    }

    suspend fun recent(limit: Int = 100) = dao.getRecent(limit)
}

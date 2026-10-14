package com.reellock.reellock.commitment

/** Nodes of the need.md section 4 state diagram. */
enum class CommitmentStatus {
    SETUP,
    READY,
    ACTIVE,
    RECOVERY_PENDING,
    RECOVERED,
    EXPIRED,
}

/** Classifier confidence for a recorded usage interval; see ScreenClassifier. */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}

enum class RecoveryRequestStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CONSUMED,
}

/** Recovery is scoped to a specific action, never blanket settings access (need.md section 7). */
enum class RecoveryAction {
    UNLOCK_CONFIG_EDIT,
    EXTEND_ALLOWANCE_TODAY,
    RESET_COMMITMENT,
}

enum class SecurityEventType {
    ACTIVATION,
    UPDATE_REJECTED_LOCKED,
    UPDATE_REJECTED_INVALID,
    EXPIRY_REACHED,
    RECOVERY_REQUESTED,
    RECOVERY_APPROVED,
    RECOVERY_REJECTED,
    CLOCK_ANOMALY_DETECTED,
    PERMISSION_LOST,
    PERMISSION_RESTORED,
    BOOT_RECONCILED,
    DEVICE_OWNER_POLICY_APPLIED,
}

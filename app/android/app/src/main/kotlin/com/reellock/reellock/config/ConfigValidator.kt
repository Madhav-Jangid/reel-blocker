package com.reellock.reellock.config

/**
 * R1: "Allowance and window are editable before activation." This validates
 * the *ranges* the setup wizard allows; CommitmentEngine separately enforces
 * *when* changes are allowed at all.
 */
object ConfigValidator {

    const val MIN_ALLOWANCE_SECONDS = 60L
    const val MAX_ALLOWANCE_SECONDS = 24L * 3_600
    const val MIN_WINDOW_SECONDS = 300L
    const val MAX_WINDOW_SECONDS = 7L * 24 * 3_600
    const val MIN_COMMITMENT_DAYS = 1
    const val MAX_COMMITMENT_DAYS = 365

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }

    fun validate(config: CommitmentConfig): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.reelsAllowanceSeconds !in MIN_ALLOWANCE_SECONDS..MAX_ALLOWANCE_SECONDS) {
            errors += "reelsAllowanceSeconds must be between $MIN_ALLOWANCE_SECONDS and $MAX_ALLOWANCE_SECONDS"
        }
        if (config.windowSeconds !in MIN_WINDOW_SECONDS..MAX_WINDOW_SECONDS) {
            errors += "windowSeconds must be between $MIN_WINDOW_SECONDS and $MAX_WINDOW_SECONDS"
        }
        if (config.reelsAllowanceSeconds > config.windowSeconds) {
            errors += "reelsAllowanceSeconds cannot exceed windowSeconds (allowance must fit inside its own window)"
        }
        if (config.commitmentDays !in MIN_COMMITMENT_DAYS..MAX_COMMITMENT_DAYS) {
            errors += "commitmentDays must be between $MIN_COMMITMENT_DAYS and $MAX_COMMITMENT_DAYS"
        }
        if (config.targetPackage.isBlank()) {
            errors += "targetPackage must not be blank"
        }
        // `version` is deliberately not validated here: it is assigned by
        // CommitmentEngine.saveDraft, not by the caller. A brand-new draft
        // from the UI legitimately has no version yet (sent as 0).

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

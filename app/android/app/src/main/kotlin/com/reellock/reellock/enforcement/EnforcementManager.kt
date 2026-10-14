package com.reellock.reellock.enforcement

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.reellock.reellock.classifier.ScreenClassification
import com.reellock.reellock.classifier.ScreenType
import com.reellock.reellock.commitment.CommitmentEngine
import com.reellock.reellock.commitment.CommitmentStatus
import com.reellock.reellock.usage.UsageAccountingEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * need.md section 5 "Blocking decision" flowchart. This is the only piece
 * that is allowed to call startReelsSession/endReelsSession/show-overlay -
 * the accessibility service just feeds it classifications.
 */
class EnforcementManager(
    private val commitmentEngine: CommitmentEngine,
    private val usageAccountingEngine: UsageAccountingEngine,
) {
    @Volatile var eventEmitter: ReelLockEventEmitter? = null

    private var overlayManager: BlockOverlayManager? = null
    private val mutex = Mutex()

    @Volatile
    private var currentlyBlocked: Boolean = false

    // Accessibility events can fire every ~150ms; only log when the
    // classification actually changes, so logcat reads like a timeline of
    // real actions instead of noise. Filter with: adb logcat -s ReelLock
    private var lastLoggedLabel: String? = null
    private var lastLoggedRemainingSeconds: Long = -1

    fun attach(service: AccessibilityService) {
        overlayManager = BlockOverlayManager(service)
    }

    fun detach() {
        overlayManager?.dismiss()
        overlayManager = null
    }

    suspend fun onClassification(service: AccessibilityService, classification: ScreenClassification) = mutex.withLock {
        val snapshot = commitmentEngine.getSnapshot()
        val label = classificationLabel(classification)
        val confidence = confidenceLabel(classification)
        eventEmitter?.emitClassification(label, confidence)

        if (label != lastLoggedLabel) {
            Log.d(TAG, "Screen -> $label (confidence=$confidence)")
            lastLoggedLabel = label
        }

        when (classification) {
            ScreenClassification.Transition -> {
                // need.md: "Re-evaluate after UI settles." Deliberately does nothing.
            }

            ScreenClassification.Unknown -> {
                usageAccountingEngine.endReelsSession("UNKNOWN_SCREEN")
                val blockUnknown = snapshot.config?.blockUnknownScreens == true &&
                    snapshot.status == CommitmentStatus.ACTIVE
                if (blockUnknown) {
                    Log.d(TAG, "Blocking: screen unrecognized and blockUnknownScreens is on")
                    showGenericBlock(service, "This Instagram screen could not be confidently identified, and your settings require blocking unknown screens during the commitment period.")
                } else {
                    dismissOverlay()
                }
            }

            is ScreenClassification.Classified -> {
                if (classification.screen != ScreenType.REELS) {
                    usageAccountingEngine.endReelsSession("LEFT_REELS")
                    dismissOverlay()
                    return@withLock
                }

                if (snapshot.status != CommitmentStatus.ACTIVE || snapshot.config == null) {
                    // No active commitment: nothing to enforce, allow.
                    dismissOverlay()
                    return@withLock
                }

                val config = snapshot.config
                val budget = usageAccountingEngine.getRemainingBudget(config.reelsAllowanceSeconds, config.windowSeconds)

                if (budget.remainingSeconds <= 0) {
                    usageAccountingEngine.endReelsSession("BUDGET_EXHAUSTED")
                    val deadlineText = snapshot.deadlineEpochMillis?.let { formatDate(it) } ?: "unknown"
                    val remainingDays = snapshot.deadlineEpochMillis?.let { daysUntil(it, snapshot.nowEpochMillis) } ?: 0
                    if (!currentlyBlocked) {
                        Log.d(TAG, "Reels budget exhausted (used ${budget.usedSeconds}s of ${budget.allowanceSeconds}s) -> showing block overlay")
                    }
                    showBudgetBlock(service, remainingDays, deadlineText)
                    // GLOBAL_ACTION_BACK was unreliable here: Reels' full-screen
                    // pager often has no back stack to pop, so it did nothing.
                    // GLOBAL_ACTION_HOME always leaves Instagram. This fires on
                    // EVERY blocked+REELS detection (not just the first), so
                    // that reopening Instagram straight back into Reels while
                    // still blocked gets kicked out again immediately, instead
                    // of only the first time.
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    currentlyBlocked = true
                    eventEmitter?.emitBlockStateChanged(true, 0)
                } else {
                    if (budget.remainingSeconds != lastLoggedRemainingSeconds) {
                        Log.d(TAG, "Reels session tracked: ${budget.usedSeconds}s used, ${budget.remainingSeconds}s remaining (confidence=${classification.confidence})")
                        lastLoggedRemainingSeconds = budget.remainingSeconds
                    }
                    usageAccountingEngine.startReelsSession(classification.confidence)
                    dismissOverlay()
                    if (currentlyBlocked) {
                        Log.d(TAG, "Unblocked: budget has room again (${budget.remainingSeconds}s remaining)")
                        eventEmitter?.emitBlockStateChanged(false, budget.remainingSeconds)
                    }
                    currentlyBlocked = false
                }
            }
        }
    }

    private fun showBudgetBlock(service: AccessibilityService, remainingDays: Long, deadlineText: String) {
        if (overlayManager == null) attach(service)
        overlayManager?.show(remainingDays, deadlineText)
    }

    private fun showGenericBlock(service: AccessibilityService, message: String) {
        if (overlayManager == null) attach(service)
        overlayManager?.show(0, message)
    }

    private fun dismissOverlay() {
        overlayManager?.dismiss()
    }

    private fun classificationLabel(c: ScreenClassification): String = when (c) {
        ScreenClassification.Transition -> "TRANSITION"
        ScreenClassification.Unknown -> "UNKNOWN"
        is ScreenClassification.Classified -> c.screen.name
    }

    private fun confidenceLabel(c: ScreenClassification): String = when (c) {
        is ScreenClassification.Classified -> c.confidence.name
        else -> "N/A"
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))

    private fun daysUntil(epochMillis: Long, nowEpochMillis: Long): Long =
        ((epochMillis - nowEpochMillis) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)

    companion object {
        private const val TAG = "ReelLock"
    }
}

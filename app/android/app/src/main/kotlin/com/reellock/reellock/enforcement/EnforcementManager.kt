package com.reellock.reellock.enforcement

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.reellock.reellock.accessibility.InstagramNavigation
import com.reellock.reellock.classifier.ScreenClassification
import com.reellock.reellock.classifier.ScreenType
import com.reellock.reellock.commitment.CommitmentEngine
import com.reellock.reellock.commitment.CommitmentSnapshot
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

    /** The last non-Reels screen actually confirmed (not mid-debounce) - so the block action can return the user there instead of always Feed. */
    private var lastNonReelsScreen: ScreenType? = null

    // Real Reels playback can report a single frame as "not visible" during
    // normal buffer swaps/page recycling (confirmed from real-device logs:
    // classification flips REELS -> UNKNOWN -> REELS every ~150ms tick while
    // actively watching). Reacting to every tick made the block overlay
    // itself flicker open/closed in sync with that noise. Leaving Reels is
    // only treated as real once it's seen for two consecutive ticks in a
    // row; a single-tick blip is ignored.
    private var consecutiveNonReelsTicks = 0

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
                // need.md: "Re-evaluate after UI settles." Deliberately does
                // nothing - doesn't count toward the leave-Reels debounce either.
            }

            ScreenClassification.Unknown -> {
                handleLeavingReels(service, snapshot, reason = "UNKNOWN_SCREEN", isUnknown = true)
            }

            is ScreenClassification.Classified -> {
                if (classification.screen != ScreenType.REELS) {
                    lastNonReelsScreen = classification.screen
                    handleLeavingReels(service, snapshot, reason = "LEFT_REELS", isUnknown = false)
                    return@withLock
                }

                consecutiveNonReelsTicks = 0 // confirmed genuinely still on Reels

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
                    // Returns to whichever tab the user was on before Reels
                    // (see InstagramNavigation), not always Feed. Fires on
                    // every tick while still blocked, not just the first:
                    // Instagram hides its bottom-nav chrome during full
                    // immersive Reels playback, so the first attempt often
                    // finds nothing to click and falls back to a weaker
                    // action - retrying means it doesn't just give up after
                    // one try that may not have actually moved the screen.
                    InstagramNavigation.goToPreviousScreen(service, lastNonReelsScreen)
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

    private suspend fun handleLeavingReels(service: AccessibilityService, snapshot: CommitmentSnapshot, reason: String, isUnknown: Boolean) {
        consecutiveNonReelsTicks++
        if (consecutiveNonReelsTicks < LEAVE_REELS_DEBOUNCE_TICKS) {
            return
        }

        usageAccountingEngine.endReelsSession(reason)
        val blockUnknown = isUnknown &&
            snapshot.config?.blockUnknownScreens == true &&
            snapshot.status == CommitmentStatus.ACTIVE
        if (blockUnknown) {
            Log.d(TAG, "Blocking: screen unrecognized and blockUnknownScreens is on")
            showGenericBlock(service, "This Instagram screen could not be confidently identified, and your settings require blocking unknown screens during the commitment period.")
        } else {
            dismissOverlay()
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
        private const val LEAVE_REELS_DEBOUNCE_TICKS = 2
    }
}

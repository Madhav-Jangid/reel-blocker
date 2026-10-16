package com.reellock.reellock.enforcement

import android.accessibilityservice.AccessibilityService
import android.util.Log
import com.reellock.reellock.accessibility.AppNavigation
import com.reellock.reellock.classifier.ScreenClassification
import com.reellock.reellock.classifier.ScreenType
import com.reellock.reellock.classifier.TargetApp
import com.reellock.reellock.commitment.CommitmentEngine
import com.reellock.reellock.commitment.CommitmentSnapshot
import com.reellock.reellock.commitment.CommitmentStatus
import com.reellock.reellock.monitoring.ReelLockForegroundService
import com.reellock.reellock.usage.UsageAccountingEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * need.md section 5 "Blocking decision" flowchart. This is the only piece
 * that is allowed to call startReelsSession/endReelsSession/show-overlay -
 * the accessibility service just feeds it classifications. The budget is
 * shared across every app in config.monitoredApps (usageAccountingEngine
 * has no notion of "which app" at all, by construction) - a tick from
 * whichever monitored app is in SHORT_VIDEO draws from the same pool.
 *
 * Sequencing for a budget-exhausted detection is deliberate: redirect first,
 * show the "limit reached" sheet only once that redirect is confirmed to
 * have worked (the user is actually seen on a different screen). Showing it
 * immediately while still classified as being on the short-video screen
 * meant the very next tick's "left short video" handling would tear it
 * straight back down the moment the redirect succeeded - looked like the
 * sheet "wasn't showing" even though it technically had been, for a few
 * hundred milliseconds.
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

    /** Set while budget-exhausted + still on the short-video screen; consumed (shown) the moment we confirm the redirect actually worked. Never shown while still classified as being on that screen. */
    private var pendingLimitNotice: LimitNotice? = null

    /** Whether the currently-visible overlay (if any) is the "limit reached" sheet, as opposed to the separate blockUnknownScreens generic overlay. Only the user's own "Got it" tap clears this - ordinary leaving-short-video bookkeeping must not auto-dismiss it. */
    private var limitSheetShowing = false

    // Real short-video playback can report a single frame as "not visible"
    // during normal buffer swaps/page recycling (confirmed from real-device
    // logs on Instagram: classification flips SHORT_VIDEO -> UNKNOWN ->
    // SHORT_VIDEO every ~150ms tick while actively watching). Reacting to
    // every tick made the block overlay itself flicker open/closed in sync
    // with that noise. Leaving the short-video screen is only treated as
    // real once it's seen for two consecutive ticks in a row; a single-tick
    // blip is ignored.
    private var consecutiveNonShortVideoTicks = 0

    // Accessibility events can fire every ~150ms; only log when the
    // classification actually changes, so logcat reads like a timeline of
    // real actions instead of noise. Filter with: adb logcat -s ReelLock
    private var lastLoggedLabel: String? = null
    private var lastLoggedRemainingSeconds: Long = -1

    private data class LimitNotice(val app: TargetApp, val remainingDays: Long, val deadlineText: String)

    fun attach(service: AccessibilityService) {
        val manager = BlockOverlayManager(service)
        manager.onUserDismiss = { limitSheetShowing = false }
        overlayManager = manager
    }

    fun detach() {
        overlayManager?.dismiss()
        overlayManager = null
        limitSheetShowing = false
        pendingLimitNotice = null
    }

    suspend fun onClassification(service: AccessibilityService, app: TargetApp, classification: ScreenClassification) = mutex.withLock {
        val snapshot = commitmentEngine.getSnapshot()

        // The accessibility service receives events from every supported
        // app (both are always in accessibility_service_config.xml's
        // packageNames), but the user only chose some of them to actually
        // be monitored/limited (CommitmentConfig.monitoredApps) - an event
        // from an app they didn't opt into is a complete no-op, not even
        // counted toward the leave-debounce.
        if (snapshot.config != null && app.packageName !in snapshot.config.monitoredApps) {
            return@withLock
        }

        val label = classificationLabel(classification)
        val confidence = confidenceLabel(classification)
        eventEmitter?.emitClassification(label, confidence)

        if (label != lastLoggedLabel) {
            Log.d(TAG, "Screen -> $label (confidence=$confidence, app=${app.name})")
            lastLoggedLabel = label
        }

        when (classification) {
            ScreenClassification.Transition -> {
                // need.md: "Re-evaluate after UI settles." Deliberately does
                // nothing - doesn't count toward the leave debounce either.
            }

            ScreenClassification.Unknown -> {
                handleLeavingShortVideo(service, snapshot, app, reason = "UNKNOWN_SCREEN", isUnknown = true)
            }

            is ScreenClassification.Classified -> {
                if (classification.screen != ScreenType.SHORT_VIDEO) {
                    handleLeavingShortVideo(service, snapshot, app, reason = "LEFT_SHORT_VIDEO", isUnknown = false)
                    return@withLock
                }

                consecutiveNonShortVideoTicks = 0 // confirmed genuinely still on the short-video screen

                if (snapshot.status != CommitmentStatus.ACTIVE || snapshot.config == null) {
                    // No active commitment: nothing to enforce, allow.
                    dismissOverlay()
                    return@withLock
                }

                val config = snapshot.config
                val budget = usageAccountingEngine.getRemainingBudget(config.reelsAllowanceSeconds, config.windowSeconds)
                ReelLockForegroundService.updateBudgetStatus(service, budget.remainingSeconds, budget.allowanceSeconds)

                if (budget.remainingSeconds <= 0) {
                    usageAccountingEngine.endReelsSession("BUDGET_EXHAUSTED")
                    val deadlineText = snapshot.deadlineEpochMillis?.let { formatDate(it) } ?: "unknown"
                    val remainingDays = snapshot.deadlineEpochMillis?.let { daysUntil(it, snapshot.nowEpochMillis) } ?: 0
                    if (!currentlyBlocked) {
                        Log.d(TAG, "Short-video budget exhausted (used ${budget.usedSeconds}s of ${budget.allowanceSeconds}s) -> redirecting off ${app.displayName}")
                    }
                    // Not shown yet - only queued. The sheet appears once
                    // handleLeavingShortVideo confirms the redirect below
                    // actually worked (see the class doc comment for why).
                    pendingLimitNotice = LimitNotice(app, remainingDays, deadlineText)
                    // Fires on every tick while still blocked, not just the
                    // first: apps like Instagram hide their bottom-nav chrome
                    // during full immersive playback, so the first attempt
                    // often finds nothing to click and falls back to a
                    // weaker action - retrying means it doesn't just give up
                    // after one try that may not have actually moved the screen.
                    AppNavigation.goToPreviousScreen(service, app)
                    currentlyBlocked = true
                    eventEmitter?.emitBlockStateChanged(true, 0)
                } else {
                    if (budget.remainingSeconds != lastLoggedRemainingSeconds) {
                        Log.d(TAG, "Short-video session tracked: ${budget.usedSeconds}s used, ${budget.remainingSeconds}s remaining (confidence=${classification.confidence})")
                        lastLoggedRemainingSeconds = budget.remainingSeconds
                    }
                    usageAccountingEngine.startReelsSession(classification.confidence)
                    // Covers the edge case where the rolling window frees up
                    // budget again while the limit sheet is still showing
                    // (user hasn't tapped "Got it" yet) and the short-video
                    // screen is re-detected - the lock reason is gone, so clear it.
                    dismissOverlay()
                    if (currentlyBlocked) {
                        Log.d(TAG, "Unblocked: budget has room again (${budget.remainingSeconds}s remaining)")
                        eventEmitter?.emitBlockStateChanged(false, budget.remainingSeconds)
                    }
                    currentlyBlocked = false
                    pendingLimitNotice = null
                }
            }
        }
    }

    private suspend fun handleLeavingShortVideo(service: AccessibilityService, snapshot: CommitmentSnapshot, app: TargetApp, reason: String, isUnknown: Boolean) {
        consecutiveNonShortVideoTicks++
        if (consecutiveNonShortVideoTicks < LEAVE_DEBOUNCE_TICKS) {
            return
        }

        usageAccountingEngine.endReelsSession(reason)

        val notice = pendingLimitNotice
        if (notice != null) {
            // The redirect just succeeded - this is the first confirmed
            // non-short-video screen since the budget ran out. Show the sheet now.
            Log.d(TAG, "Redirect confirmed - showing limit-reached sheet")
            if (overlayManager == null) attach(service)
            overlayManager?.show(notice.app, notice.remainingDays, notice.deadlineText)
            limitSheetShowing = true
            pendingLimitNotice = null
            return
        }

        val blockUnknown = isUnknown &&
            snapshot.config?.blockUnknownScreens == true &&
            snapshot.status == CommitmentStatus.ACTIVE
        if (blockUnknown) {
            Log.d(TAG, "Blocking: screen unrecognized and blockUnknownScreens is on")
            showGenericBlock(service, app, "This ${app.appName} screen could not be confidently identified, and your settings require blocking unknown screens during the commitment period.")
        } else if (!limitSheetShowing) {
            // Never auto-dismiss the limit sheet from here - only the
            // user's own "Got it" tap (see attach()'s onUserDismiss) does.
            dismissOverlay()
        }
    }

    private fun showGenericBlock(service: AccessibilityService, app: TargetApp, message: String) {
        if (overlayManager == null) attach(service)
        overlayManager?.show(app, 0, message, isGenericMessage = true)
    }

    /** Always resets [limitSheetShowing] too - the only place that must survive a dismiss is handleLeavingShortVideo's debounced skip, which never calls this. */
    private fun dismissOverlay() {
        overlayManager?.dismiss()
        limitSheetShowing = false
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
        private const val LEAVE_DEBOUNCE_TICKS = 2
    }
}

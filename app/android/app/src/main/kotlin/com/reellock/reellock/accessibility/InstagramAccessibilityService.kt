package com.reellock.reellock.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.reellock.reellock.ReelLockApplication
import com.reellock.reellock.classifier.InstagramUiSignatures
import com.reellock.reellock.classifier.ScreenClassifier
import com.reellock.reellock.monitoring.ReelLockForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * need.md section 3/5/10. Deliberately thin: all it does is filter events to
 * Instagram, dump the node tree through ScreenClassifier, and hand the
 * result to EnforcementManager. Event types, flags and the Instagram package
 * filter are declared in res/xml/accessibility_service_config.xml so the
 * service only ever receives what it needs.
 */
class InstagramAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected - watching ${InstagramUiSignatures.INSTAGRAM_PACKAGE}")
        val app = application as ReelLockApplication
        app.enforcementManager.attach(this)
        // Keeps this process (and this service) alive after the app's own
        // UI task is swiped from Recents - see ReelLockForegroundService's
        // doc comment for why this was necessary.
        ReelLockForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName != InstagramUiSignatures.INSTAGRAM_PACKAGE) return

        val rootNode = rootInActiveWindow ?: return
        val uiNode = AccessibilityNodeInfoUiNode(rootNode)
        val classification = ScreenClassifier.classify(uiNode)
        val resourceIds = ScreenClassifier.dumpResourceIds(uiNode)

        val app = application as ReelLockApplication
        app.enforcementManager.eventEmitter?.emitDiagnostics(resourceIds)
        scope.launch {
            app.enforcementManager.onClassification(this@InstagramAccessibilityService, classification)
        }
    }

    override fun onInterrupt() {
        // No ongoing speech/feedback to interrupt; nothing to do.
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility service stopped - Reels detection is OFF until it is re-enabled")
        val app = application as ReelLockApplication
        app.enforcementManager.detach()
        ReelLockForegroundService.stop(this)
        scope.cancel()
    }

    companion object {
        private const val TAG = "ReelLock"
    }
}

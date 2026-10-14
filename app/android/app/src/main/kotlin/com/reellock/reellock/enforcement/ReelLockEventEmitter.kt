package com.reellock.reellock.enforcement

/** Bridged to the Flutter EventChannel by MainActivity; null (no-op) whenever no Flutter engine is attached (e.g. the service runs without the activity in the foreground). */
interface ReelLockEventEmitter {
    fun emitBlockStateChanged(blocked: Boolean, remainingSeconds: Long)
    fun emitClassification(screenType: String, confidence: String)
    fun emitPermissionDegraded(reason: String)
    /** Diagnostic-only, see ScreenClassifier.dumpResourceIds. */
    fun emitDiagnostics(resourceIds: List<String>)
}

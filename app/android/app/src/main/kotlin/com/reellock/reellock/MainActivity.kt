package com.reellock.reellock

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.reellock.reellock.bridge.toMap
import com.reellock.reellock.bridge.configFromMap
import com.reellock.reellock.commitment.RecoveryAction
import com.reellock.reellock.commitment.UpdateResult
import com.reellock.reellock.commitment.ActivationResult
import com.reellock.reellock.enforcement.ReelLockEventEmitter
import com.reellock.reellock.recovery.RecoveryApprovalResult
import com.reellock.reellock.recovery.RecoveryRequestResult
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONTROL_CHANNEL = "reellock/control"
private const val EVENTS_CHANNEL = "reellock/events"

class MainActivity : FlutterActivity() {

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var eventSink: EventChannel.EventSink? = null

    private val app: ReelLockApplication get() = application as ReelLockApplication

    // SurfaceView (Flutter's default render mode) renders solid black on some
    // emulator/virtual-GPU compositors - the app runs fine underneath (verified
    // via logcat: no crash, bridge calls succeed), it just never becomes
    // visible. TextureView is slightly slower but composites correctly
    // everywhere. Safe to revert to the default (remove this override) once
    // confirmed fine on a real device.
    override fun getRenderMode(): RenderMode = RenderMode.texture

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CONTROL_CHANNEL)
            .setMethodCallHandler { call, result -> handleMethodCall(call.method, call.arguments, result) }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENTS_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
                    eventSink = sink
                    app.enforcementManager.eventEmitter = buildEmitter()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    app.enforcementManager.eventEmitter = null
                }
            },
        )
    }

    private fun buildEmitter(): ReelLockEventEmitter = object : ReelLockEventEmitter {
        override fun emitBlockStateChanged(blocked: Boolean, remainingSeconds: Long) {
            postEvent(mapOf("type" to "blockStateChanged", "blocked" to blocked, "remainingSeconds" to remainingSeconds))
        }

        override fun emitClassification(screenType: String, confidence: String) {
            postEvent(mapOf("type" to "classification", "screenType" to screenType, "confidence" to confidence))
        }

        override fun emitPermissionDegraded(reason: String) {
            postEvent(mapOf("type" to "permissionDegraded", "reason" to reason))
        }

        override fun emitDiagnostics(resourceIds: List<String>) {
            postEvent(mapOf("type" to "diagnostics", "resourceIds" to resourceIds))
        }
    }

    private fun postEvent(event: Map<String, Any?>) {
        mainHandler.post { eventSink?.success(event) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMethodCall(method: String, arguments: Any?, result: MethodChannel.Result) {
        val args = arguments as? Map<String, Any?> ?: emptyMap()
        mainScope.launch {
            try {
                when (method) {
                    "getSnapshot" -> {
                        val snapshot = withContext(Dispatchers.IO) { app.commitmentEngine.getSnapshot() }
                        result.success(snapshot.toMap())
                    }

                    "saveDraftConfig" -> {
                        val configMap = args["config"] as Map<String, Any?>
                        val trustedKey = args["trustedPersonPublicKeyBase64"] as? String
                        val config = configFromMap(configMap)
                        val outcome = withContext(Dispatchers.IO) { app.commitmentEngine.saveDraft(config, trustedKey) }
                        result.success(updateResultToMap(outcome))
                    }

                    "activateCommitment" -> {
                        val outcome = withContext(Dispatchers.IO) { app.commitmentEngine.activate() }
                        result.success(activationResultToMap(outcome))
                    }

                    "beginReconfiguration" -> {
                        val outcome = withContext(Dispatchers.IO) { app.commitmentEngine.beginReconfiguration() }
                        result.success(updateResultToMap(outcome))
                    }

                    "getUsageSummary" -> {
                        val snapshot = withContext(Dispatchers.IO) { app.commitmentEngine.getSnapshot() }
                        val config = snapshot.config
                        if (config == null) {
                            result.success(null)
                        } else {
                            val budget = withContext(Dispatchers.IO) {
                                app.usageAccountingEngine.getRemainingBudget(config.reelsAllowanceSeconds, config.windowSeconds)
                            }
                            result.success(budget.toMap())
                        }
                    }

                    "getPermissionStatus" -> {
                        val status = withContext(Dispatchers.IO) { app.permissionMonitor.check() }
                        result.success(status.toMap())
                    }

                    "getSecurityEvents" -> {
                        val limit = (args["limit"] as? Number)?.toInt() ?: 100
                        val events = withContext(Dispatchers.IO) { app.securityEventLogger.recent(limit) }
                        result.success(events.map { it.toMap() })
                    }

                    "createRecoveryRequest" -> {
                        val reason = args["reason"] as? String ?: ""
                        val action = RecoveryAction.valueOf(args["action"] as String)
                        val outcome = withContext(Dispatchers.IO) { app.recoveryManager.createRequest(reason, action) }
                        result.success(recoveryRequestResultToMap(outcome))
                    }

                    "submitRecoveryApproval" -> {
                        val nonce = args["nonce"] as String
                        val signature = args["signatureBase64"] as String
                        val outcome = withContext(Dispatchers.IO) { app.recoveryManager.submitApproval(nonce, signature) }
                        result.success(recoveryApprovalResultToMap(outcome))
                    }

                    "applyManagedModePolicies" -> {
                        val status = withContext(Dispatchers.IO) { app.devicePolicyController.applyManagedModePolicies() }
                        result.success(status.toMap())
                    }

                    "getManagedModeStatus" -> {
                        val status = withContext(Dispatchers.IO) { app.devicePolicyController.currentStatus() }
                        result.success(status.toMap())
                    }

                    "openAccessibilitySettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(null)
                    }

                    "openDeviceAdminSettings" -> {
                        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                android.content.ComponentName(this@MainActivity, com.reellock.reellock.deviceadmin.ReelLockDeviceAdminReceiver::class.java),
                            )
                            putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets ReelLock apply supported managed-device restrictions. This alone does NOT make it a device owner; see the README for that.")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        result.success(null)
                    }

                    "openAppSettings" -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        result.success(null)
                    }

                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("REELLOCK_ERROR", e.message, null)
            }
        }
    }

    private fun updateResultToMap(r: UpdateResult): Map<String, Any?> = when (r) {
        is UpdateResult.Updated -> mapOf("outcome" to "updated", "version" to r.version)
        UpdateResult.Locked -> mapOf("outcome" to "locked")
        UpdateResult.NoActiveCommitment -> mapOf("outcome" to "noActiveCommitment")
        is UpdateResult.Invalid -> mapOf("outcome" to "invalid", "errors" to r.errors)
    }

    private fun activationResultToMap(r: ActivationResult): Map<String, Any?> = when (r) {
        is ActivationResult.Activated -> mapOf("outcome" to "activated", "configVersion" to r.configVersion, "deadlineEpochMillis" to r.deadlineEpochMillis)
        ActivationResult.NoDraft -> mapOf("outcome" to "noDraft")
        is ActivationResult.Invalid -> mapOf("outcome" to "invalid", "errors" to r.errors)
    }

    private fun recoveryRequestResultToMap(r: RecoveryRequestResult): Map<String, Any?> = when (r) {
        is RecoveryRequestResult.Created -> mapOf("outcome" to "created", "nonce" to r.nonce, "challenge" to r.challenge, "expiresAtEpochMillis" to r.expiresAtEpochMillis)
        RecoveryRequestResult.NoActiveCommitment -> mapOf("outcome" to "noActiveCommitment")
        RecoveryRequestResult.NoTrustedPersonKeyConfigured -> mapOf("outcome" to "noTrustedPersonKeyConfigured")
    }

    private fun recoveryApprovalResultToMap(r: RecoveryApprovalResult): Map<String, Any?> = when (r) {
        is RecoveryApprovalResult.Approved -> mapOf("outcome" to "approved", "action" to r.action.name)
        RecoveryApprovalResult.NotFound -> mapOf("outcome" to "notFound")
        RecoveryApprovalResult.Expired -> mapOf("outcome" to "expired")
        RecoveryApprovalResult.AlreadyResolved -> mapOf("outcome" to "alreadyResolved")
        RecoveryApprovalResult.InvalidSignature -> mapOf("outcome" to "invalidSignature")
        RecoveryApprovalResult.NoTrustedPersonKeyConfigured -> mapOf("outcome" to "noTrustedPersonKeyConfigured")
    }
}

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Single point of contact with the Kotlin enforcement core (see
/// android/app/src/main/kotlin/com/reellock/reellock/MainActivity.kt).
/// The Flutter UI never decides lock state, remaining budget or recovery
/// validity itself - every one of those calls is a round trip here.
///
/// Every call and every live event is logged with debugPrint under the
/// '[ReelLock]' prefix, so `flutter run`'s debug console reads like a plain
/// timeline of what actually happened, instead of Gradle/Flutter build noise.
class NativeBridge {
  NativeBridge._();
  static final NativeBridge instance = NativeBridge._();

  static const MethodChannel _control = MethodChannel('reellock/control');
  static const EventChannel _events = EventChannel('reellock/events');

  Stream<Map<String, dynamic>>? _eventStream;

  /// Live classification/block-state/permission/diagnostics events from the accessibility service.
  Stream<Map<String, dynamic>> get events {
    _eventStream ??= _events.receiveBroadcastStream().map((event) {
      final map = Map<String, dynamic>.from(event as Map);
      _logEvent(map);
      return map;
    });
    return _eventStream!;
  }

  Future<Map<String, dynamic>> getSnapshot() => _invokeMap('getSnapshot');

  Future<Map<String, dynamic>> saveDraftConfig(
    Map<String, dynamic> config, {
    String? trustedPersonPublicKeyBase64,
  }) =>
      _invokeMap('saveDraftConfig', {
        'config': config,
        'trustedPersonPublicKeyBase64': trustedPersonPublicKeyBase64,
      });

  Future<Map<String, dynamic>> activateCommitment() => _invokeMap('activateCommitment');

  Future<Map<String, dynamic>> beginReconfiguration() => _invokeMap('beginReconfiguration');

  Future<Map<String, dynamic>?> getUsageSummary() async {
    _logCall('getUsageSummary', null);
    final result = await _control.invokeMethod('getUsageSummary');
    if (result == null) {
      _logResult('getUsageSummary', 'no active configuration');
      return null;
    }
    final map = Map<String, dynamic>.from(result as Map);
    _logResult('getUsageSummary', '${map['remainingSeconds']}s remaining of ${map['allowanceSeconds']}s');
    return map;
  }

  Future<Map<String, dynamic>> getPermissionStatus() => _invokeMap('getPermissionStatus');

  Future<List<Map<String, dynamic>>> getSecurityEvents({int limit = 100}) async {
    _logCall('getSecurityEvents', {'limit': limit});
    final result = await _control.invokeMethod('getSecurityEvents', {'limit': limit});
    final list = (result as List).map((e) => Map<String, dynamic>.from(e as Map)).toList();
    _logResult('getSecurityEvents', '${list.length} events');
    return list;
  }

  Future<Map<String, dynamic>> createRecoveryRequest(String reason, String action) =>
      _invokeMap('createRecoveryRequest', {'reason': reason, 'action': action});

  Future<Map<String, dynamic>> submitRecoveryApproval(String nonce, String signatureBase64) =>
      _invokeMap('submitRecoveryApproval', {'nonce': nonce, 'signatureBase64': signatureBase64});

  Future<Map<String, dynamic>> applyManagedModePolicies() => _invokeMap('applyManagedModePolicies');

  Future<Map<String, dynamic>> getManagedModeStatus() => _invokeMap('getManagedModeStatus');

  Future<void> openAccessibilitySettings() => _invokeVoid('openAccessibilitySettings');

  Future<void> openDeviceAdminSettings() => _invokeVoid('openDeviceAdminSettings');

  Future<void> openAppSettings() => _invokeVoid('openAppSettings');

  /// Requests POST_NOTIFICATIONS at runtime (Android 13+; a no-op success on
  /// older versions). Returns whether it ended up granted.
  Future<bool> requestNotificationPermission() async {
    _logCall('requestNotificationPermission', null);
    final granted = await _control.invokeMethod('requestNotificationPermission') as bool;
    _logResult('requestNotificationPermission', granted ? 'granted' : 'denied');
    return granted;
  }

  /// Shows the system's own "allow background activity" confirmation dialog.
  Future<void> requestIgnoreBatteryOptimizations() => _invokeVoid('requestIgnoreBatteryOptimizations');

  /// Best-effort deep link into MIUI's Autostart settings (falls back to the app's details page on other OEMs/MIUI versions).
  Future<void> openMiuiAutostartSettings() => _invokeVoid('openMiuiAutostartSettings');

  Future<Map<String, dynamic>> _invokeMap(String method, [Map<String, dynamic>? args]) async {
    _logCall(method, args);
    final result = await _control.invokeMethod(method, args);
    final map = Map<String, dynamic>.from(result as Map);
    _logResult(method, map['outcome']?.toString() ?? map.toString());
    return map;
  }

  Future<void> _invokeVoid(String method, [Map<String, dynamic>? args]) async {
    _logCall(method, args);
    await _control.invokeMethod(method, args);
  }

  void _logCall(String method, Map<String, dynamic>? args) {
    debugPrint('[ReelLock] -> $method${args == null ? '' : '($args)'}');
  }

  void _logResult(String method, String summary) {
    debugPrint('[ReelLock] <- $method: $summary');
  }

  String? _lastLoggedClassification;
  String? _lastLoggedResourceIds;

  void _logEvent(Map<String, dynamic> event) {
    switch (event['type']) {
      case 'classification':
        final label = 'screen=${event['screenType']} confidence=${event['confidence']}';
        // Accessibility events fire every ~150ms; only log on change, or the
        // console is unreadable.
        if (label != _lastLoggedClassification) {
          debugPrint('[ReelLock] event: $label');
          _lastLoggedClassification = label;
        }
      case 'blockStateChanged':
        debugPrint('[ReelLock] event: blocked=${event['blocked']} remainingSeconds=${event['remainingSeconds']}');
      case 'permissionDegraded':
        debugPrint('[ReelLock] event: permission degraded - ${event['reason']}');
      case 'diagnostics':
        final ids = List<String>.from(event['resourceIds'] as List);
        final joined = ids.join(', ');
        // Only log when the actual set of ids changes - this is the raw
        // material for calibrating InstagramUiSignatures.kt, so print the
        // real strings, not just a count.
        if (joined != _lastLoggedResourceIds) {
          debugPrint('[ReelLock] event: diagnostics (${ids.length} ids): $joined');
          _lastLoggedResourceIds = joined;
        }
      default:
        debugPrint('[ReelLock] event: $event');
    }
  }
}

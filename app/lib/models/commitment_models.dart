/// Mirrors android/.../config/CommitmentConfig.kt and commitment/Domain.kt.
/// Kept as plain value classes (no codegen) since the schema is small and
/// stable; see bridge/native_bridge.dart for the wire format.
library;

enum CommitmentStatus { setup, ready, active, recoveryPending, recovered, expired }

CommitmentStatus commitmentStatusFromString(String value) => switch (value) {
      'SETUP' => CommitmentStatus.setup,
      'READY' => CommitmentStatus.ready,
      'ACTIVE' => CommitmentStatus.active,
      'RECOVERY_PENDING' => CommitmentStatus.recoveryPending,
      'RECOVERED' => CommitmentStatus.recovered,
      'EXPIRED' => CommitmentStatus.expired,
      _ => CommitmentStatus.setup,
    };

enum RecoveryMode { trustedPerson }

enum EnforcementMode { accessibility, deviceManaged }

enum RecoveryAction { unlockConfigEdit, extendAllowanceToday, resetCommitment }

extension RecoveryActionWire on RecoveryAction {
  String get wireName => switch (this) {
        RecoveryAction.unlockConfigEdit => 'UNLOCK_CONFIG_EDIT',
        RecoveryAction.extendAllowanceToday => 'EXTEND_ALLOWANCE_TODAY',
        RecoveryAction.resetCommitment => 'RESET_COMMITMENT',
      };

  String get label => switch (this) {
        RecoveryAction.unlockConfigEdit => 'Unlock settings for editing',
        RecoveryAction.extendAllowanceToday => "Extend today's allowance (not enforced yet)",
        RecoveryAction.resetCommitment => 'End the commitment early',
      };
}

class CommitmentConfig {
  final int version;
  final int reelsAllowanceSeconds;
  final int windowSeconds;
  final int commitmentDays;
  final RecoveryMode recoveryMode;
  final EnforcementMode enforcementMode;
  final String targetPackage;
  final bool blockUnknownScreens;
  final bool allowRecoveryDuringLock;

  const CommitmentConfig({
    required this.version,
    required this.reelsAllowanceSeconds,
    required this.windowSeconds,
    required this.commitmentDays,
    required this.recoveryMode,
    required this.enforcementMode,
    required this.targetPackage,
    required this.blockUnknownScreens,
    required this.allowRecoveryDuringLock,
  });

  static const defaultConfig = CommitmentConfig(
    version: 0,
    reelsAllowanceSeconds: 1800,
    windowSeconds: 18000,
    commitmentDays: 30,
    recoveryMode: RecoveryMode.trustedPerson,
    enforcementMode: EnforcementMode.accessibility,
    targetPackage: 'com.instagram.android',
    blockUnknownScreens: false,
    allowRecoveryDuringLock: true,
  );

  factory CommitmentConfig.fromMap(Map<String, dynamic> map) => CommitmentConfig(
        version: map['version'] as int,
        reelsAllowanceSeconds: (map['reelsAllowanceSeconds'] as num).toInt(),
        windowSeconds: (map['windowSeconds'] as num).toInt(),
        commitmentDays: map['commitmentDays'] as int,
        recoveryMode: RecoveryMode.trustedPerson,
        enforcementMode: (map['enforcementMode'] as String) == 'DEVICE_MANAGED'
            ? EnforcementMode.deviceManaged
            : EnforcementMode.accessibility,
        targetPackage: map['targetPackage'] as String,
        blockUnknownScreens: map['blockUnknownScreens'] as bool,
        allowRecoveryDuringLock: map['allowRecoveryDuringLock'] as bool,
      );

  Map<String, dynamic> toMap() => {
        'version': version,
        'reelsAllowanceSeconds': reelsAllowanceSeconds,
        'windowSeconds': windowSeconds,
        'commitmentDays': commitmentDays,
        'recoveryMode': 'TRUSTED_PERSON',
        'enforcementMode': enforcementMode == EnforcementMode.deviceManaged ? 'DEVICE_MANAGED' : 'ACCESSIBILITY',
        'targetPackage': targetPackage,
        'blockUnknownScreens': blockUnknownScreens,
        'allowRecoveryDuringLock': allowRecoveryDuringLock,
      };

  CommitmentConfig copyWith({
    int? reelsAllowanceSeconds,
    int? windowSeconds,
    int? commitmentDays,
    EnforcementMode? enforcementMode,
    bool? blockUnknownScreens,
  }) => CommitmentConfig(
        version: version,
        reelsAllowanceSeconds: reelsAllowanceSeconds ?? this.reelsAllowanceSeconds,
        windowSeconds: windowSeconds ?? this.windowSeconds,
        commitmentDays: commitmentDays ?? this.commitmentDays,
        recoveryMode: recoveryMode,
        enforcementMode: enforcementMode ?? this.enforcementMode,
        targetPackage: targetPackage,
        blockUnknownScreens: blockUnknownScreens ?? this.blockUnknownScreens,
        allowRecoveryDuringLock: allowRecoveryDuringLock,
      );
}

class CommitmentSnapshot {
  final CommitmentStatus status;
  final CommitmentConfig? config;
  final int? activatedAtEpochMillis;
  final int? deadlineEpochMillis;
  final int nowEpochMillis;
  final bool authorizedRecovery;
  final bool clockAnomalyDetected;

  const CommitmentSnapshot({
    required this.status,
    required this.config,
    required this.activatedAtEpochMillis,
    required this.deadlineEpochMillis,
    required this.nowEpochMillis,
    required this.authorizedRecovery,
    required this.clockAnomalyDetected,
  });

  factory CommitmentSnapshot.fromMap(Map<String, dynamic> map) => CommitmentSnapshot(
        status: commitmentStatusFromString(map['status'] as String),
        config: map['config'] == null ? null : CommitmentConfig.fromMap(Map<String, dynamic>.from(map['config'] as Map)),
        activatedAtEpochMillis: (map['activatedAtEpochMillis'] as num?)?.toInt(),
        deadlineEpochMillis: (map['deadlineEpochMillis'] as num?)?.toInt(),
        nowEpochMillis: (map['nowEpochMillis'] as num).toInt(),
        authorizedRecovery: map['authorizedRecovery'] as bool,
        clockAnomalyDetected: map['clockAnomalyDetected'] as bool,
      );

  Duration? get timeUntilDeadline {
    if (deadlineEpochMillis == null) return null;
    final diff = deadlineEpochMillis! - nowEpochMillis;
    return Duration(milliseconds: diff < 0 ? 0 : diff);
  }
}

class UsageSummary {
  final int remainingSeconds;
  final int usedSeconds;
  final int allowanceSeconds;
  final int windowSeconds;
  final int lowConfidenceSecondsCounted;

  const UsageSummary({
    required this.remainingSeconds,
    required this.usedSeconds,
    required this.allowanceSeconds,
    required this.windowSeconds,
    required this.lowConfidenceSecondsCounted,
  });

  factory UsageSummary.fromMap(Map<String, dynamic> map) => UsageSummary(
        remainingSeconds: (map['remainingSeconds'] as num).toInt(),
        usedSeconds: (map['usedSeconds'] as num).toInt(),
        allowanceSeconds: (map['allowanceSeconds'] as num).toInt(),
        windowSeconds: (map['windowSeconds'] as num).toInt(),
        lowConfidenceSecondsCounted: (map['lowConfidenceSecondsCounted'] as num).toInt(),
      );

  double get fractionRemaining => allowanceSeconds == 0 ? 0 : remainingSeconds / allowanceSeconds;
}

enum ProtectionLevel { active, degraded }

class PermissionStatus {
  final bool accessibilityServiceEnabled;
  final bool notificationsEnabled;
  final bool batteryOptimizationIgnored;
  final bool isDeviceOwner;
  final bool isMiui;
  final ProtectionLevel protectionLevel;

  const PermissionStatus({
    required this.accessibilityServiceEnabled,
    required this.notificationsEnabled,
    required this.batteryOptimizationIgnored,
    required this.isDeviceOwner,
    required this.isMiui,
    required this.protectionLevel,
  });

  // Defensive (`as bool? ?? false`) rather than a hard cast: during
  // development the Dart and native sides can briefly be rebuilt out of
  // sync (an old installed APK missing a newer field), and a missing
  // optional status should degrade to "not granted", not crash the app.
  factory PermissionStatus.fromMap(Map<String, dynamic> map) => PermissionStatus(
        accessibilityServiceEnabled: map['accessibilityServiceEnabled'] as bool? ?? false,
        notificationsEnabled: map['notificationsEnabled'] as bool? ?? false,
        batteryOptimizationIgnored: map['batteryOptimizationIgnored'] as bool? ?? false,
        isDeviceOwner: map['isDeviceOwner'] as bool? ?? false,
        isMiui: map['isMiui'] as bool? ?? false,
        protectionLevel: (map['protectionLevel'] as String?) == 'ACTIVE' ? ProtectionLevel.active : ProtectionLevel.degraded,
      );
}

class SecurityEvent {
  final String type;
  final int timestampEpochMillis;
  final int? configVersion;
  final String outcome;
  final String? details;

  const SecurityEvent({
    required this.type,
    required this.timestampEpochMillis,
    required this.configVersion,
    required this.outcome,
    required this.details,
  });

  factory SecurityEvent.fromMap(Map<String, dynamic> map) => SecurityEvent(
        type: map['type'] as String,
        timestampEpochMillis: (map['timestampEpochMillis'] as num).toInt(),
        configVersion: (map['configVersion'] as num?)?.toInt(),
        outcome: map['outcome'] as String,
        details: map['details'] as String?,
      );
}

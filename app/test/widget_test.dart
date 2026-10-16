import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:reellock/main.dart';

void main() {
  const controlChannel = MethodChannel('reellock/control');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(controlChannel, null);
  });

  testWidgets('with no commitment yet, the app opens on the setup wizard', (tester) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(controlChannel, (call) async {
      if (call.method == 'getSnapshot') {
        return {
          'status': 'SETUP',
          'config': null,
          'activatedAtEpochMillis': null,
          'deadlineEpochMillis': null,
          'nowEpochMillis': 1700000000000,
          'authorizedRecovery': false,
          'clockAnomalyDetected': false,
        };
      }
      return null;
    });

    await tester.pumpWidget(const ReelLockApp());
    await tester.pumpAndSettle();

    expect(find.text('Set up ReelLock'), findsOneWidget);
    // The button is below the fold in a ListView (Slivers only build what's
    // visible), so scroll it into view before asserting on it. The number
    // input fields each have their own internal Scrollable (for cursor
    // scrolling), so find.byType(Scrollable) now matches several - the
    // outer ListView's is the first one built.
    await tester.scrollUntilVisible(find.text('Review and activate'), 300, scrollable: find.byType(Scrollable).first);
    await tester.pumpAndSettle();
    expect(find.text('Review and activate'), findsOneWidget);
  });

  testWidgets('with an active commitment, the app opens on the dashboard', (tester) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(controlChannel, (call) async {
      switch (call.method) {
        case 'getSnapshot':
          return {
            'status': 'ACTIVE',
            'config': {
              'version': 1,
              'reelsAllowanceSeconds': 1800,
              'windowSeconds': 18000,
              'commitmentDays': 30,
              'recoveryMode': 'TRUSTED_PERSON',
              'enforcementMode': 'ACCESSIBILITY',
              'monitoredApps': ['com.instagram.android'],
              'blockUnknownScreens': false,
              'allowRecoveryDuringLock': true,
            },
            'activatedAtEpochMillis': 1700000000000,
            'deadlineEpochMillis': 1702592000000,
            'nowEpochMillis': 1700000000000,
            'authorizedRecovery': false,
            'clockAnomalyDetected': false,
          };
        case 'getUsageSummary':
          return {
            'remainingSeconds': 1200,
            'usedSeconds': 600,
            'allowanceSeconds': 1800,
            'windowSeconds': 18000,
            'lowConfidenceSecondsCounted': 0,
          };
        case 'getPermissionStatus':
          return {
            'accessibilityServiceEnabled': true,
            'notificationsEnabled': true,
            'batteryOptimizationIgnored': true,
            'isDeviceOwner': false,
            'isMiui': false,
            'protectionLevel': 'ACTIVE',
          };
        case 'getSecurityEvents':
          return <Map<String, dynamic>>[];
        default:
          return null;
      }
    });

    await tester.pumpWidget(const ReelLockApp());
    await tester.pumpAndSettle();

    // HomeShell's AppBar title for the Dashboard tab (index 0).
    expect(find.text('ReelLock'), findsOneWidget);
    // StatusPill label for an ACTIVE (non-recovery-pending) commitment.
    expect(find.text('Active'), findsOneWidget);
    // BudgetGauge's small label, e.g. "of 30 min left".
    expect(find.textContaining('min left'), findsOneWidget);
  });
}

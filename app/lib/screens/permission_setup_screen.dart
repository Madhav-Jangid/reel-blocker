import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';
import '../widgets/permission_tile.dart';

/// need.md R9 "transparent failure handling": activating a commitment
/// without the accessibility service enabled would silently claim
/// protection that doesn't actually exist - the service is the only thing
/// that ever detects Reels. This screen is the gate between "draft saved"
/// and "commitment activated": it asks for every permission ReelLock needs
/// up front, blocks activation until the one that actually matters
/// (accessibility) is granted, and only then calls activateCommitment.
class PermissionSetupScreen extends StatefulWidget {
  final EnforcementMode enforcementMode;
  final VoidCallback onActivated;

  const PermissionSetupScreen({super.key, required this.enforcementMode, required this.onActivated});

  @override
  State<PermissionSetupScreen> createState() => _PermissionSetupScreenState();
}

class _PermissionSetupScreenState extends State<PermissionSetupScreen> with WidgetsBindingObserver {
  PermissionStatus? _status;
  bool _notificationRequested = false;
  bool _activating = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Coming back from Settings (accessibility toggle, app details, etc.) -
    // re-check rather than making the user tap a manual refresh button.
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final status = await NativeBridge.instance.getPermissionStatus();
    if (!mounted) return;
    setState(() => _status = PermissionStatus.fromMap(status));
  }

  Future<void> _requestNotifications() async {
    await NativeBridge.instance.requestNotificationPermission();
    _notificationRequested = true;
    await _refresh();
  }

  Future<void> _activate() async {
    setState(() {
      _activating = true;
      _error = null;
    });
    try {
      final result = await NativeBridge.instance.activateCommitment();
      if (result['outcome'] == 'activated') {
        if (mounted) Navigator.of(context).pop();
        widget.onActivated();
      } else {
        setState(() => _error = 'Activation failed: ${result['outcome']}');
      }
    } finally {
      if (mounted) setState(() => _activating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    final accessibilityGranted = status?.accessibilityServiceEnabled ?? false;
    final notificationsGranted = status?.notificationsEnabled ?? false;
    final canActivate = accessibilityGranted && !_activating;

    return Scaffold(
      appBar: AppBar(title: const Text('Grant permissions')),
      body: status == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(AppSpacing.m),
              children: [
                Text(
                  "ReelLock needs these before the commitment can start - without them it would "
                  'activate the lock while unable to actually detect short-form video.',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: AppSpacing.l),
                PermissionTile(
                  icon: Icons.accessibility_new,
                  title: 'Accessibility service',
                  subtitle: 'Required. This is the only thing that detects when a monitored app shows short-form video.',
                  granted: accessibilityGranted,
                  required: true,
                  actionLabel: 'Open settings',
                  onAction: () => NativeBridge.instance.openAccessibilitySettings(),
                ),
                const SizedBox(height: AppSpacing.s),
                PermissionTile(
                  icon: Icons.notifications_outlined,
                  title: 'Notifications',
                  subtitle: 'Optional. Shows the "ReelLock is active" status while it watches in the background.',
                  granted: notificationsGranted,
                  required: false,
                  actionLabel: _notificationRequested && !notificationsGranted ? 'Open settings' : 'Allow',
                  onAction: _notificationRequested && !notificationsGranted
                      ? () => NativeBridge.instance.openAppSettings()
                      : _requestNotifications,
                ),
                const SizedBox(height: AppSpacing.s),
                PermissionTile(
                  icon: Icons.battery_charging_full,
                  title: 'Background activity',
                  subtitle: 'Optional, but strongly recommended. Without this, Android may kill ReelLock (and its '
                      'detection) a few minutes after you leave a monitored app or swipe ReelLock away.',
                  granted: status.batteryOptimizationIgnored,
                  required: false,
                  actionLabel: 'Allow',
                  onAction: () => NativeBridge.instance.requestIgnoreBatteryOptimizations(),
                ),
                if (status.isMiui) ...[
                  const SizedBox(height: AppSpacing.s),
                  PermissionTile(
                    icon: Icons.phonelink_lock_outlined,
                    title: 'MIUI Autostart',
                    subtitle: "Optional, but needed on MIUI specifically - it has its own background-kill switch on "
                        "top of Android's. Can't be checked automatically; turn it on for ReelLock on the screen that opens.",
                    granted: false,
                    required: false,
                    statusUnverifiable: true,
                    actionLabel: 'Open settings',
                    onAction: () => NativeBridge.instance.openMiuiAutostartSettings(),
                  ),
                ],
                if (widget.enforcementMode == EnforcementMode.deviceManaged) ...[
                  const SizedBox(height: AppSpacing.s),
                  PermissionTile(
                    icon: Icons.admin_panel_settings_outlined,
                    title: 'Device admin (managed mode)',
                    subtitle: 'Optional. Only takes effect if this device is provisioned as device owner - see README. '
                        'Falls back to standard protection otherwise.',
                    granted: status.isDeviceOwner,
                    required: false,
                    actionLabel: 'Open settings',
                    onAction: () => NativeBridge.instance.openDeviceAdminSettings(),
                  ),
                ],
                const SizedBox(height: AppSpacing.l),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: AppSpacing.s),
                    child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ),
                FilledButton(
                  onPressed: canActivate ? _activate : null,
                  child: _activating
                      ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('Activate commitment'),
                ),
                if (!accessibilityGranted)
                  Padding(
                    padding: const EdgeInsets.only(top: AppSpacing.s),
                    child: Text(
                      'Enable the accessibility service above to continue.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
              ],
            ),
    );
  }
}

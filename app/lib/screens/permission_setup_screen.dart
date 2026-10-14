import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';

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
              padding: const EdgeInsets.all(20),
              children: [
                const Text(
                  'ReelLock needs these before the commitment can start - without them it would '
                  'activate the lock while unable to actually detect Reels, which need.md explicitly '
                  'treats as a failure to surface, never to hide.',
                ),
                const SizedBox(height: 20),
                _PermissionTile(
                  icon: Icons.accessibility_new,
                  title: 'Accessibility service',
                  subtitle: 'Required. This is the only thing that detects when Reels is open.',
                  granted: accessibilityGranted,
                  required: true,
                  actionLabel: 'Open settings',
                  onAction: () => NativeBridge.instance.openAccessibilitySettings(),
                ),
                const SizedBox(height: 12),
                _PermissionTile(
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
                const SizedBox(height: 12),
                _PermissionTile(
                  icon: Icons.battery_charging_full,
                  title: 'Background activity',
                  subtitle: 'Optional, but strongly recommended. Without this, Android may kill ReelLock (and its '
                      'detection) a few minutes after you leave Instagram or swipe the app away.',
                  granted: status.batteryOptimizationIgnored,
                  required: false,
                  actionLabel: 'Allow',
                  onAction: () => NativeBridge.instance.requestIgnoreBatteryOptimizations(),
                ),
                if (status.isMiui) ...[
                  const SizedBox(height: 12),
                  _PermissionTile(
                    icon: Icons.phonelink_lock_outlined,
                    title: 'MIUI Autostart',
                    subtitle: 'Optional, but needed on MIUI specifically - it has its own background-kill switch on '
                        "top of Android's. Can't be checked automatically; turn it on for ReelLock on the screen that opens.",
                    granted: false,
                    required: false,
                    statusUnverifiable: true,
                    actionLabel: 'Open settings',
                    onAction: () => NativeBridge.instance.openMiuiAutostartSettings(),
                  ),
                ],
                if (widget.enforcementMode == EnforcementMode.deviceManaged) ...[
                  const SizedBox(height: 12),
                  _PermissionTile(
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
                const SizedBox(height: 28),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(_error!, style: const TextStyle(color: Colors.red)),
                  ),
                FilledButton(
                  onPressed: canActivate ? _activate : null,
                  child: _activating
                      ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Text('Activate commitment'),
                ),
                if (!accessibilityGranted)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text(
                      'Enable the accessibility service above to continue.',
                      style: TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool granted;
  final bool required;
  final String actionLabel;
  final VoidCallback onAction;
  /// True for permissions Android gives no API to check (e.g. MIUI
  /// Autostart) - always shows the action button, never a false checkmark.
  final bool statusUnverifiable;

  const _PermissionTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.granted,
    required this.required,
    required this.actionLabel,
    required this.onAction,
    this.statusUnverifiable = false,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: granted ? Colors.green : null),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(title, style: Theme.of(context).textTheme.titleMedium),
                      if (required) ...[
                        const SizedBox(width: 6),
                        const Text('Required', style: TextStyle(fontSize: 11, color: Colors.red)),
                      ],
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(subtitle, style: const TextStyle(fontSize: 12, color: Colors.grey)),
                ],
              ),
            ),
            const SizedBox(width: 8),
            (granted && !statusUnverifiable)
                ? const Icon(Icons.check_circle, color: Colors.green)
                : OutlinedButton(onPressed: onAction, child: Text(actionLabel)),
          ],
        ),
      ),
    );
  }
}

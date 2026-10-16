import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';
import '../widgets/app_card.dart';
import '../widgets/permission_tile.dart';
import 'recovery_screen.dart';

/// Settings tab content (no own Scaffold - [HomeShell] owns that): the
/// active commitment's config as a read-only summary, a permission-status
/// recap (same tile pattern as the pre-activation gate), and the entry
/// point into trusted-person recovery.
class SettingsTab extends StatefulWidget {
  final CommitmentSnapshot snapshot;
  final VoidCallback onNeedsRefresh;

  const SettingsTab({super.key, required this.snapshot, required this.onNeedsRefresh});

  @override
  State<SettingsTab> createState() => _SettingsTabState();
}

class _SettingsTabState extends State<SettingsTab> {
  PermissionStatus? _permission;

  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() async {
    final status = await NativeBridge.instance.getPermissionStatus();
    if (!mounted) return;
    setState(() => _permission = PermissionStatus.fromMap(status));
  }

  @override
  Widget build(BuildContext context) {
    final config = widget.snapshot.config;
    final permission = _permission;

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.m),
        children: [
          Text('Commitment', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.s),
          if (config == null)
            const AppCard(child: Text('No active configuration'))
          else
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SettingsRow(
                    label: 'Monitored apps',
                    value: config.monitoredApps
                        .map((p) => TargetApp.values.firstWhere((a) => a.packageName == p, orElse: () => TargetApp.instagram).displayName)
                        .join(', '),
                  ),
                  _SettingsRow(label: 'Shared allowance', value: '${config.reelsAllowanceSeconds ~/ 60} min'),
                  _SettingsRow(label: 'Rolling window', value: '${(config.windowSeconds / 3600).toStringAsFixed(1)} hrs'),
                  _SettingsRow(label: 'Configuration lock', value: '${config.commitmentDays} days'),
                  _SettingsRow(
                    label: 'Enforcement mode',
                    value: config.enforcementMode == EnforcementMode.deviceManaged ? 'Managed' : 'Standard',
                  ),
                  _SettingsRow(label: 'Unknown screens', value: config.blockUnknownScreens ? 'Blocked' : 'Allowed', isLast: true),
                ],
              ),
            ),
          const SizedBox(height: AppSpacing.l),
          Text('Permissions', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.s),
          if (permission == null)
            const Center(child: Padding(padding: EdgeInsets.all(AppSpacing.l), child: CircularProgressIndicator()))
          else ...[
            PermissionTile(
              icon: Icons.accessibility_new,
              title: 'Accessibility service',
              subtitle: 'Detects when a monitored app shows short-form video.',
              granted: permission.accessibilityServiceEnabled,
              required: true,
              actionLabel: 'Open settings',
              onAction: () => NativeBridge.instance.openAccessibilitySettings(),
            ),
            const SizedBox(height: AppSpacing.s),
            PermissionTile(
              icon: Icons.battery_charging_full,
              title: 'Background activity',
              subtitle: 'Keeps detection running after you leave the app.',
              granted: permission.batteryOptimizationIgnored,
              required: false,
              actionLabel: 'Allow',
              onAction: () => NativeBridge.instance.requestIgnoreBatteryOptimizations(),
            ),
            if (permission.isMiui) ...[
              const SizedBox(height: AppSpacing.s),
              PermissionTile(
                icon: Icons.phonelink_lock_outlined,
                title: 'MIUI Autostart',
                subtitle: "MIUI's own background-kill switch, separate from Android's.",
                granted: false,
                required: false,
                statusUnverifiable: true,
                actionLabel: 'Open settings',
                onAction: () => NativeBridge.instance.openMiuiAutostartSettings(),
              ),
            ],
          ],
          const SizedBox(height: AppSpacing.l),
          Text('Recovery', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.s),
          AppCard(
            onTap: () async {
              await Navigator.push(context, MaterialPageRoute(builder: (_) => const RecoveryScreen()));
              widget.onNeedsRefresh();
            },
            child: Row(
              children: [
                const Icon(Icons.support_agent_rounded),
                const SizedBox(width: AppSpacing.m),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Request trusted-person recovery', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: AppSpacing.xs),
                      Text(
                        'Scoped to one specific action, authorized by your trusted person - never a blanket unlock.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsRow extends StatelessWidget {
  final String label;
  final String value;
  final bool isLast;

  const _SettingsRow({required this.label, required this.value, this.isLast = false});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: isLast ? 0 : AppSpacing.s),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodyMedium),
          Text(value, style: Theme.of(context).textTheme.titleSmall),
        ],
      ),
    );
  }
}

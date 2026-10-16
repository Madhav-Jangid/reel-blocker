import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';
import '../widgets/app_card.dart';
import '../widgets/budget_gauge.dart';
import '../widgets/status_pill.dart';

/// Dashboard tab content (no own Scaffold/AppBar - [HomeShell] owns those).
/// Reflects CommitmentEngine + UsageAccountingEngine state. Never computes
/// remaining budget or lock status itself - every number here came from a
/// bridge call to the Kotlin side.
class DashboardTab extends StatefulWidget {
  final CommitmentSnapshot snapshot;

  const DashboardTab({super.key, required this.snapshot});

  @override
  State<DashboardTab> createState() => _DashboardTabState();
}

class _DashboardTabState extends State<DashboardTab> {
  UsageSummary? _usage;
  PermissionStatus? _permission;
  StreamSubscription<Map<String, dynamic>>? _eventSub;
  String? _liveClassification;
  DateTime _lastUsageRefresh = DateTime.fromMillisecondsSinceEpoch(0);

  @override
  void initState() {
    super.initState();
    _refresh();
    _eventSub = NativeBridge.instance.events.listen(
      (event) {
        if (!mounted) return;
        if (event['type'] == 'classification') {
          setState(() => _liveClassification = '${event['screenType']} (${event['confidence']})');
          // Accessibility events can fire every ~150ms while Reels is open;
          // throttle the usage re-fetch so "minutes remaining" still ticks
          // down live without hammering the platform channel on every frame.
          if (DateTime.now().difference(_lastUsageRefresh) > const Duration(seconds: 2)) {
            _lastUsageRefresh = DateTime.now();
            _refresh();
          }
        } else if (event['type'] == 'blockStateChanged') {
          _refresh();
        }
      },
      // The live event stream is a convenience, not a correctness dependency
      // (remaining budget / lock state always come from explicit bridge
      // calls above) - so a transport error here is swallowed rather than
      // crashing the dashboard.
      onError: (_) {},
    );
  }

  Future<void> _refresh() async {
    final usage = await NativeBridge.instance.getUsageSummary();
    final permission = await NativeBridge.instance.getPermissionStatus();
    if (!mounted) return;
    setState(() {
      _usage = usage == null ? null : UsageSummary.fromMap(usage);
      _permission = PermissionStatus.fromMap(permission);
    });
  }

  @override
  void dispose() {
    _eventSub?.cancel();
    super.dispose();
  }

  String _formatDuration(Duration d) {
    if (d.inDays > 0) return '${d.inDays}d ${d.inHours % 24}h';
    if (d.inHours > 0) return '${d.inHours}h ${d.inMinutes % 60}m';
    return '${d.inMinutes}m';
  }

  @override
  Widget build(BuildContext context) {
    final snapshot = widget.snapshot;
    final deadline = snapshot.timeUntilDeadline;
    final usage = _usage;
    final permission = _permission;
    final scheme = Theme.of(context).colorScheme;

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.m),
        children: [
          if (permission != null && permission.protectionLevel == ProtectionLevel.degraded)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.m),
              child: AppCard(
                color: scheme.errorContainer,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.warning_amber_rounded, color: scheme.onErrorContainer),
                    const SizedBox(width: AppSpacing.m),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Protection is degraded', style: Theme.of(context).textTheme.titleSmall?.copyWith(color: scheme.onErrorContainer)),
                          const SizedBox(height: AppSpacing.xs),
                          Text(
                            'The accessibility service is off, so short-form video cannot be detected right now.',
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: scheme.onErrorContainer),
                          ),
                          const SizedBox(height: AppSpacing.s),
                          FilledButton(
                            onPressed: () => NativeBridge.instance.openAccessibilitySettings(),
                            child: const Text('Fix now'),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          if (snapshot.clockAnomalyDetected)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.m),
              child: AppCard(
                child: Row(
                  children: [
                    const Icon(Icons.schedule_rounded),
                    const SizedBox(width: AppSpacing.m),
                    Expanded(
                      child: Text(
                        'A system-clock change was detected and ignored for commitment timing.',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          AppCard(
            padding: const EdgeInsets.symmetric(vertical: AppSpacing.l, horizontal: AppSpacing.m),
            child: Column(
              children: [
                if (usage == null)
                  Text('No active configuration', style: Theme.of(context).textTheme.bodyMedium)
                else
                  BudgetGauge(
                    fractionRemaining: usage.fractionRemaining,
                    bigLabel: '${usage.remainingSeconds ~/ 60}',
                    smallLabel: 'of ${usage.allowanceSeconds ~/ 60} min left',
                  ),
                const SizedBox(height: AppSpacing.m),
                if (usage != null)
                  Text(
                    'Rolling ${(usage.windowSeconds / 3600).toStringAsFixed(1)}h window',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                if (usage != null && usage.lowConfidenceSecondsCounted > 0)
                  Padding(
                    padding: const EdgeInsets.only(top: AppSpacing.xs),
                    child: Text(
                      '${usage.lowConfidenceSecondsCounted}s counted at low detection confidence',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.m),
          AppCard(
            child: Row(
              children: [
                StatusPill(
                  label: snapshot.status == CommitmentStatus.recoveryPending ? 'Recovery pending' : 'Active',
                  color: snapshot.status == CommitmentStatus.recoveryPending ? AppSeverity.warning : AppSeverity.success,
                  icon: snapshot.status == CommitmentStatus.recoveryPending ? Icons.hourglass_top_rounded : Icons.lock_rounded,
                ),
                const SizedBox(width: AppSpacing.m),
                if (deadline != null)
                  Expanded(
                    child: Text(
                      'Locked for ${_formatDuration(deadline)} more',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
              ],
            ),
          ),
          if (_liveClassification != null)
            Padding(
              padding: const EdgeInsets.only(top: AppSpacing.m),
              child: Text(
                'Last seen screen: $_liveClassification',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
        ],
      ),
    );
  }
}

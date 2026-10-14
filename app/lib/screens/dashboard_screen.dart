import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import 'recovery_screen.dart';
import 'security_log_screen.dart';
import 'diagnostics_screen.dart';

/// Reflects CommitmentEngine + UsageAccountingEngine state. Never computes
/// remaining budget or lock status itself - every number here came from a
/// bridge call to the Kotlin side.
class DashboardScreen extends StatefulWidget {
  final CommitmentSnapshot snapshot;
  final VoidCallback onNeedsRefresh;

  const DashboardScreen({super.key, required this.snapshot, required this.onNeedsRefresh});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('ReelLock'),
        actions: [
          IconButton(
            icon: const Icon(Icons.bug_report_outlined),
            tooltip: 'Diagnostics',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DiagnosticsScreen())),
          ),
          IconButton(
            icon: const Icon(Icons.history),
            tooltip: 'Security log',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SecurityLogScreen())),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            if (permission != null && permission.protectionLevel == ProtectionLevel.degraded)
              Card(
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  leading: const Icon(Icons.warning_amber),
                  title: const Text('Protection is degraded'),
                  subtitle: const Text('The accessibility service is off, so Reels cannot be detected right now. This is shown, never hidden (need.md R9).'),
                  trailing: FilledButton(
                    onPressed: () => NativeBridge.instance.openAccessibilitySettings(),
                    child: const Text('Fix'),
                  ),
                ),
              ),
            if (snapshot.clockAnomalyDetected)
              const Card(
                child: ListTile(
                  leading: Icon(Icons.schedule),
                  title: Text('Clock anomaly detected'),
                  subtitle: Text('A system-clock change was detected and ignored for commitment timing (see time/TrustedTimeManager.kt).'),
                ),
              ),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Commitment', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    Text(snapshot.status == CommitmentStatus.recoveryPending ? 'Recovery pending approval' : 'Active - locked'),
                    if (deadline != null) Text('Locked for ${_formatDuration(deadline)} more'),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Reels budget', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 8),
                    if (usage == null)
                      const Text('No active configuration')
                    else ...[
                      LinearProgressIndicator(value: usage.fractionRemaining.clamp(0, 1)),
                      const SizedBox(height: 8),
                      Text('${usage.remainingSeconds ~/ 60} min remaining of ${usage.allowanceSeconds ~/ 60} min, '
                          'per ${(usage.windowSeconds / 3600).toStringAsFixed(1)}h rolling window'),
                      if (usage.lowConfidenceSecondsCounted > 0)
                        Text(
                          '${usage.lowConfidenceSecondsCounted}s counted at low detection confidence',
                          style: const TextStyle(fontStyle: FontStyle.italic, fontSize: 12),
                        ),
                    ],
                    if (_liveClassification != null) Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text('Last seen Instagram screen: $_liveClassification', style: const TextStyle(fontSize: 12, color: Colors.grey)),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              icon: const Icon(Icons.support_agent),
              label: const Text('Request trusted-person recovery'),
              onPressed: () async {
                await Navigator.push(context, MaterialPageRoute(builder: (_) => const RecoveryScreen()));
                widget.onNeedsRefresh();
              },
            ),
          ],
        ),
      ),
    );
  }
}

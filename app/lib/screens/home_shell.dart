import 'package:flutter/material.dart';
import '../models/commitment_models.dart';
import 'activity_tab.dart';
import 'dashboard_tab.dart';
import 'settings_tab.dart';

/// Replaces the old flat push-stack (Dashboard with AppBar icon buttons that
/// pushed Security Log / Diagnostics as separate screens) with a proper
/// bottom-nav shell, for the `active` / `recoveryPending` commitment states.
/// Onboarding/occasional flows (setup wizard, permission gate,
/// post-commitment, recovery) stay as their own full-screen pushed routes
/// outside this shell - they're one-time/transient, not ongoing app usage.
class HomeShell extends StatefulWidget {
  final CommitmentSnapshot snapshot;
  final VoidCallback onNeedsRefresh;

  const HomeShell({super.key, required this.snapshot, required this.onNeedsRefresh});

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  static const _titles = ['ReelLock', 'Activity', 'Settings'];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_titles[_index])),
      body: IndexedStack(
        index: _index,
        children: [
          DashboardTab(snapshot: widget.snapshot),
          const ActivityTab(),
          SettingsTab(snapshot: widget.snapshot, onNeedsRefresh: widget.onNeedsRefresh),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.space_dashboard_outlined), selectedIcon: Icon(Icons.space_dashboard_rounded), label: 'Dashboard'),
          NavigationDestination(icon: Icon(Icons.bolt_outlined), selectedIcon: Icon(Icons.bolt_rounded), label: 'Activity'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings_rounded), label: 'Settings'),
        ],
      ),
    );
  }
}

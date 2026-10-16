import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import 'setup_wizard_screen.dart';
import 'home_shell.dart';
import 'post_commitment_screen.dart';

/// Loads the CommitmentEngine's current state (see commitment/CommitmentEngine.kt
/// CommitmentSnapshot) and routes to the matching screen. No screen below this
/// one decides lock state on its own - they all re-fetch from here on return.
class RootScreen extends StatefulWidget {
  const RootScreen({super.key});

  @override
  State<RootScreen> createState() => _RootScreenState();
}

class _RootScreenState extends State<RootScreen> {
  Future<CommitmentSnapshot>? _future;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _future = NativeBridge.instance.getSnapshot().then(CommitmentSnapshot.fromMap);
  }

  void refresh() => setState(_load);

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<CommitmentSnapshot>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(body: Center(child: CircularProgressIndicator()));
        }
        if (snapshot.hasError) {
          return Scaffold(
            body: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.error_outline, size: 48, color: Colors.red),
                    const SizedBox(height: 12),
                    Text('Could not reach the ReelLock service:\n${snapshot.error}', textAlign: TextAlign.center),
                    const SizedBox(height: 16),
                    FilledButton(onPressed: refresh, child: const Text('Retry')),
                  ],
                ),
              ),
            ),
          );
        }

        final data = snapshot.data!;
        switch (data.status) {
          case CommitmentStatus.setup:
          case CommitmentStatus.ready:
            return SetupWizardScreen(initialConfig: data.config, onDone: refresh);
          case CommitmentStatus.active:
          case CommitmentStatus.recoveryPending:
            return HomeShell(snapshot: data, onNeedsRefresh: refresh);
          case CommitmentStatus.recovered:
          case CommitmentStatus.expired:
            return PostCommitmentScreen(snapshot: data, onReconfigured: refresh);
        }
      },
    );
  }
}

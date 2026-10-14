import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';

/// State diagram (need.md section 4): Recovered/Expired -> Setup, but only
/// on an explicit user action, never automatically.
class PostCommitmentScreen extends StatelessWidget {
  final CommitmentSnapshot snapshot;
  final VoidCallback onReconfigured;

  const PostCommitmentScreen({super.key, required this.snapshot, required this.onReconfigured});

  @override
  Widget build(BuildContext context) {
    final expired = snapshot.status == CommitmentStatus.expired;
    return Scaffold(
      appBar: AppBar(title: const Text('ReelLock')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(expired ? Icons.lock_open : Icons.verified_user, size: 56),
              const SizedBox(height: 16),
              Text(
                expired ? 'Your 30-day commitment has expired.' : 'Your trusted person authorized a recovery.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              const Text(
                'Your usage history and audit log were kept (need.md section 4: a recovery event must not silently erase usage history).',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: () async {
                  await NativeBridge.instance.beginReconfiguration();
                  onReconfigured();
                },
                child: const Text('Start a new configuration'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

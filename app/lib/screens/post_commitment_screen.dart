import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';

/// State diagram (need.md section 4): Recovered/Expired -> Setup, but only
/// on an explicit user action, never automatically.
class PostCommitmentScreen extends StatelessWidget {
  final CommitmentSnapshot snapshot;
  final VoidCallback onReconfigured;

  const PostCommitmentScreen({super.key, required this.snapshot, required this.onReconfigured});

  @override
  Widget build(BuildContext context) {
    final expired = snapshot.status == CommitmentStatus.expired;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('ReelLock')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.l),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 88,
                height: 88,
                decoration: BoxDecoration(color: scheme.primaryContainer, shape: BoxShape.circle),
                child: Icon(
                  expired ? Icons.lock_open_rounded : Icons.verified_user_rounded,
                  size: 40,
                  color: scheme.onPrimaryContainer,
                ),
              ),
              const SizedBox(height: AppSpacing.l),
              Text(
                expired ? 'Your commitment has expired.' : 'Your trusted person authorized a recovery.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppSpacing.s),
              Text(
                'Your usage history and audit log were kept - a recovery event never silently erases them.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: AppSpacing.xl),
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

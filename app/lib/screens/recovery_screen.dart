import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';
import '../widgets/app_card.dart';

/// need.md section 7: a recovery request produces a challenge the user
/// copies to their trusted person out of band (text, QR photo, whatever).
/// The trusted person signs it OFFLINE on their own device - see
/// tools/trusted_signer.js for the reference signer - and sends back a
/// signature, which gets pasted into step 2 below. ReelLock never sees the
/// trusted person's private key.
class RecoveryScreen extends StatefulWidget {
  const RecoveryScreen({super.key});

  @override
  State<RecoveryScreen> createState() => _RecoveryScreenState();
}

class _RecoveryScreenState extends State<RecoveryScreen> {
  final _reasonController = TextEditingController();
  final _signatureController = TextEditingController();
  RecoveryAction _action = RecoveryAction.unlockConfigEdit;

  String? _nonce;
  String? _challenge;
  String? _error;
  String? _resultMessage;
  bool _busy = false;

  Future<void> _createRequest() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await NativeBridge.instance.createRecoveryRequest(_reasonController.text.trim(), _action.wireName);
      switch (result['outcome']) {
        case 'created':
          setState(() {
            _nonce = result['nonce'] as String;
            _challenge = result['challenge'] as String;
          });
        case 'noTrustedPersonKeyConfigured':
          setState(() => _error = 'No trusted-person public key was saved during setup. Recovery is unavailable until one is configured.');
        default:
          setState(() => _error = 'Could not create a recovery request: ${result['outcome']}');
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submitApproval() async {
    if (_nonce == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await NativeBridge.instance.submitRecoveryApproval(_nonce!, _signatureController.text.trim());
      setState(() {
        switch (result['outcome']) {
          case 'approved':
            _resultMessage = 'Recovery approved and applied: ${result['action']}.';
          case 'invalidSignature':
            _error = 'That signature did not verify against the pinned trusted-person key.';
          case 'expired':
            _error = 'This recovery request expired. Start a new one.';
          case 'alreadyResolved':
            _error = 'This recovery request was already resolved.';
          case 'notFound':
            _error = 'Unknown recovery request.';
          default:
            _error = 'Could not apply the approval: ${result['outcome']}';
        }
      });
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    _reasonController.dispose();
    _signatureController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Trusted-person recovery')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.m),
        children: [
          if (_resultMessage != null) ...[
            AppCard(
              color: scheme.primaryContainer,
              child: Text(_resultMessage!, style: TextStyle(color: scheme.onPrimaryContainer)),
            ),
            const SizedBox(height: AppSpacing.l),
            FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
          ] else if (_nonce == null) ...[
            Text(
              'Step 1: describe what you need and why. Recovery is scoped to one specific action - it is never a blanket unlock.',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.m),
            DropdownButtonFormField<RecoveryAction>(
              initialValue: _action,
              decoration: const InputDecoration(labelText: 'Requested action'),
              items: RecoveryAction.values.map((a) => DropdownMenuItem(value: a, child: Text(a.label))).toList(),
              onChanged: (v) => setState(() => _action = v ?? RecoveryAction.unlockConfigEdit),
            ),
            const SizedBox(height: AppSpacing.m),
            TextField(
              controller: _reasonController,
              decoration: const InputDecoration(labelText: 'Reason'),
              maxLines: 2,
            ),
            const SizedBox(height: AppSpacing.l),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.s),
                child: Text(_error!, style: TextStyle(color: scheme.error)),
              ),
            FilledButton(onPressed: _busy ? null : _createRequest, child: const Text('Create recovery request')),
          ] else ...[
            Text(
              'Step 2: send the challenge below to your trusted person. They sign it on their own device '
              '(tools/trusted_signer.js) and send back a signature.',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.m),
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Challenge', style: Theme.of(context).textTheme.titleSmall),
                  const SizedBox(height: AppSpacing.xs),
                  SelectableText(_challenge ?? '', style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton.icon(
                      icon: const Icon(Icons.copy_rounded),
                      label: const Text('Copy'),
                      onPressed: () => Clipboard.setData(ClipboardData(text: _challenge ?? '')),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.l),
            Text('Once you have their signature (Base64), paste it here:', style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: AppSpacing.s),
            TextField(
              controller: _signatureController,
              maxLines: 3,
              decoration: const InputDecoration(hintText: 'MEUCIQ...'),
            ),
            const SizedBox(height: AppSpacing.l),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.s),
                child: Text(_error!, style: TextStyle(color: scheme.error)),
              ),
            FilledButton(onPressed: _busy ? null : _submitApproval, child: const Text('Submit approval')),
          ],
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';

/// need.md section 7: a recovery request produces a challenge the user
/// copies to their trusted person out of band (text, QR photo, whatever).
/// The trusted person signs it OFFLINE on their own device - see
/// tools/trusted_signer.py for the reference signer - and sends back a
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
    return Scaffold(
      appBar: AppBar(title: const Text('Trusted-person recovery')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          if (_resultMessage != null) ...[
            Card(
              color: Theme.of(context).colorScheme.primaryContainer,
              child: Padding(padding: const EdgeInsets.all(16), child: Text(_resultMessage!)),
            ),
            const SizedBox(height: 16),
            FilledButton(onPressed: () => Navigator.pop(context), child: const Text('Done')),
          ] else if (_nonce == null) ...[
            const Text('Step 1: describe what you need and why. Recovery is scoped to one specific action (need.md section 7) - it is never a blanket unlock.'),
            const SizedBox(height: 12),
            DropdownButtonFormField<RecoveryAction>(
              initialValue: _action,
              decoration: const InputDecoration(labelText: 'Requested action'),
              items: RecoveryAction.values.map((a) => DropdownMenuItem(value: a, child: Text(a.label))).toList(),
              onChanged: (v) => setState(() => _action = v ?? RecoveryAction.unlockConfigEdit),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _reasonController,
              decoration: const InputDecoration(labelText: 'Reason', border: OutlineInputBorder()),
              maxLines: 2,
            ),
            const SizedBox(height: 20),
            if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
            FilledButton(onPressed: _busy ? null : _createRequest, child: const Text('Create recovery request')),
          ] else ...[
            const Text('Step 2: send the challenge below to your trusted person. They sign it on their own device (tools/trusted_signer.py) and send back a signature.'),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Challenge', style: TextStyle(fontWeight: FontWeight.bold)),
                    SelectableText(_challenge ?? '', style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton.icon(
                        icon: const Icon(Icons.copy),
                        label: const Text('Copy'),
                        onPressed: () => Clipboard.setData(ClipboardData(text: _challenge ?? '')),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            const Text('Once you have their signature (Base64), paste it here:'),
            const SizedBox(height: 8),
            TextField(
              controller: _signatureController,
              maxLines: 3,
              decoration: const InputDecoration(border: OutlineInputBorder(), hintText: 'MEUCIQ...'),
            ),
            const SizedBox(height: 20),
            if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_error!, style: const TextStyle(color: Colors.red))),
            FilledButton(onPressed: _busy ? null : _submitApproval, child: const Text('Submit approval')),
          ],
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';

/// need.md section 4/6: configuration is fully editable here, but activation
/// creates an immutable snapshot and engages the 30-day lock. Everything
/// below only ever *proposes* a config - CommitmentEngine.saveDraft on the
/// Kotlin side is what actually accepts or rejects it (R1, R3).
class SetupWizardScreen extends StatefulWidget {
  final CommitmentConfig? initialConfig;
  final VoidCallback onDone;

  const SetupWizardScreen({super.key, required this.initialConfig, required this.onDone});

  @override
  State<SetupWizardScreen> createState() => _SetupWizardScreenState();
}

class _SetupWizardScreenState extends State<SetupWizardScreen> {
  late int _allowanceMinutes;
  late double _windowHours;
  late int _commitmentDays;
  late EnforcementMode _enforcementMode;
  late bool _blockUnknownScreens;
  final _trustedKeyController = TextEditingController();

  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final config = widget.initialConfig ?? CommitmentConfig.defaultConfig;
    _allowanceMinutes = config.reelsAllowanceSeconds ~/ 60;
    _windowHours = config.windowSeconds / 3600;
    _commitmentDays = config.commitmentDays;
    _enforcementMode = config.enforcementMode;
    _blockUnknownScreens = config.blockUnknownScreens;
  }

  CommitmentConfig _buildConfig() => CommitmentConfig(
        version: widget.initialConfig?.version ?? 0,
        reelsAllowanceSeconds: _allowanceMinutes * 60,
        windowSeconds: (_windowHours * 3600).round(),
        commitmentDays: _commitmentDays,
        recoveryMode: RecoveryMode.trustedPerson,
        enforcementMode: _enforcementMode,
        targetPackage: 'com.instagram.android',
        blockUnknownScreens: _blockUnknownScreens,
        allowRecoveryDuringLock: true,
      );

  Future<void> _saveDraft() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final result = await NativeBridge.instance.saveDraftConfig(
        _buildConfig().toMap(),
        trustedPersonPublicKeyBase64: _trustedKeyController.text.trim().isEmpty ? null : _trustedKeyController.text.trim(),
      );
      if (result['outcome'] == 'invalid') {
        setState(() => _error = (result['errors'] as List).join('\n'));
        return;
      }
      if (result['outcome'] == 'locked') {
        setState(() => _error = 'Settings are locked by an active commitment.');
        return;
      }
      await _confirmAndActivate();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _confirmAndActivate() async {
    if (_trustedKeyController.text.trim().isEmpty) {
      setState(() => _error = "A trusted person's public key is required before activation (need.md section 7).");
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Confirm your commitment'),
        content: Text(
          'Reels allowance: $_allowanceMinutes minutes per ${_windowHours.toStringAsFixed(1)} hours\n'
          'Settings locked for $_commitmentDays days\n'
          'Unknown screens: ${_blockUnknownScreens ? "blocked" : "allowed"}\n\n'
          'Once activated, NOTHING below can be changed through this app until '
          'the lock expires or your trusted person authorizes recovery.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Activate')),
        ],
      ),
    );
    if (confirmed != true) return;

    final result = await NativeBridge.instance.activateCommitment();
    if (result['outcome'] == 'activated') {
      widget.onDone();
    } else {
      setState(() => _error = 'Activation failed: ${result['outcome']}');
    }
  }

  @override
  void dispose() {
    _trustedKeyController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Set up ReelLock')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text('Reels allowance', style: Theme.of(context).textTheme.titleMedium),
          Text('$_allowanceMinutes minutes per rolling window'),
          Slider(
            value: _allowanceMinutes.toDouble(),
            min: 1,
            max: 240,
            divisions: 239,
            label: '$_allowanceMinutes min',
            onChanged: (v) => setState(() => _allowanceMinutes = v.round()),
          ),
          const SizedBox(height: 12),
          Text('Rolling window', style: Theme.of(context).textTheme.titleMedium),
          Text('${_windowHours.toStringAsFixed(1)} hours'),
          Slider(
            value: _windowHours,
            min: 0.5,
            max: 48,
            divisions: 95,
            label: '${_windowHours.toStringAsFixed(1)}h',
            onChanged: (v) => setState(() => _windowHours = v),
          ),
          const SizedBox(height: 12),
          Text('Configuration lock', style: Theme.of(context).textTheme.titleMedium),
          Text('$_commitmentDays days from activation'),
          Slider(
            value: _commitmentDays.toDouble(),
            min: 1,
            max: 365,
            divisions: 364,
            label: '$_commitmentDays d',
            onChanged: (v) => setState(() => _commitmentDays = v.round()),
          ),
          const SizedBox(height: 12),
          SwitchListTile(
            title: const Text('Block unidentified Instagram screens'),
            subtitle: const Text('Stricter, but more likely to misfire on an unrecognized screen (need.md section 5).'),
            value: _blockUnknownScreens,
            onChanged: (v) => setState(() => _blockUnknownScreens = v),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<EnforcementMode>(
            initialValue: _enforcementMode,
            decoration: const InputDecoration(labelText: 'Enforcement mode'),
            items: const [
              DropdownMenuItem(value: EnforcementMode.accessibility, child: Text('Standard (accessibility service)')),
              DropdownMenuItem(value: EnforcementMode.deviceManaged, child: Text('Managed (device-owner provisioned)')),
            ],
            onChanged: (v) => setState(() => _enforcementMode = v ?? EnforcementMode.accessibility),
          ),
          if (_enforcementMode == EnforcementMode.deviceManaged)
            const Padding(
              padding: EdgeInsets.only(top: 8),
              child: Text(
                'Managed mode only takes effect if this device is actually provisioned as device owner '
                '(see README). Otherwise ReelLock falls back to standard, accessibility-only protection '
                'and will say so on the dashboard - it never silently claims protection it cannot apply.',
                style: TextStyle(fontStyle: FontStyle.italic),
              ),
            ),
          const SizedBox(height: 20),
          Text('Trusted person', style: Theme.of(context).textTheme.titleMedium),
          const Text(
            "Paste the trusted person's EC (P-256) public key, Base64 X.509 SubjectPublicKeyInfo. "
            'They generate this once on their own device - see tools/trusted_signer.py.',
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _trustedKeyController,
            maxLines: 3,
            decoration: const InputDecoration(border: OutlineInputBorder(), hintText: 'MFkwEwYHKoZIzj0CAQ...'),
          ),
          const SizedBox(height: 20),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          FilledButton(
            onPressed: _saving ? null : _saveDraft,
            child: _saving ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2)) : const Text('Review and activate'),
          ),
        ],
      ),
    );
  }
}

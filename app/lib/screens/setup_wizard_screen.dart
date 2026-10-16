import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';
import '../widgets/app_card.dart';
import '../widgets/number_stepper_field.dart';
import 'permission_setup_screen.dart';

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
  late Set<String> _monitoredApps;
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
    _monitoredApps = Set.of(config.monitoredApps);
  }

  CommitmentConfig _buildConfig() => CommitmentConfig(
        version: widget.initialConfig?.version ?? 0,
        reelsAllowanceSeconds: _allowanceMinutes * 60,
        windowSeconds: (_windowHours * 3600).round(),
        commitmentDays: _commitmentDays,
        recoveryMode: RecoveryMode.trustedPerson,
        enforcementMode: _enforcementMode,
        monitoredApps: _monitoredApps,
        blockUnknownScreens: _blockUnknownScreens,
        allowRecoveryDuringLock: true,
      );

  Future<void> _saveDraft() async {
    if (_monitoredApps.isEmpty) {
      setState(() => _error = 'Choose at least one app to monitor.');
      return;
    }
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
          'Monitoring: ${_monitoredApps.map((p) => TargetApp.values.firstWhere((a) => a.packageName == p).displayName).join(", ")}\n'
          'Shared allowance: $_allowanceMinutes minutes per ${_windowHours.toStringAsFixed(1)} hours\n'
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
    if (!mounted) return;

    // Draft is saved and confirmed, but not yet activated - the permission
    // gate decides when (and whether) activateCommitment actually gets
    // called (need.md R9: never claim protection that can't actually run).
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => PermissionSetupScreen(
          enforcementMode: _enforcementMode,
          onActivated: widget.onDone,
        ),
      ),
    );
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
        padding: const EdgeInsets.all(AppSpacing.m),
        children: [
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Apps to monitor', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'All selected apps share one allowance below - watching either one counts against the same budget.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: AppSpacing.s),
                for (final app in TargetApp.values)
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(app.displayName),
                    value: _monitoredApps.contains(app.packageName),
                    onChanged: (checked) => setState(() {
                      if (checked ?? false) {
                        _monitoredApps.add(app.packageName);
                      } else {
                        _monitoredApps.remove(app.packageName);
                      }
                    }),
                  ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.m),
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                NumberStepperField(
                  label: 'Shared allowance',
                  value: _allowanceMinutes.toDouble(),
                  min: 1,
                  max: 240,
                  step: 1,
                  suffix: 'min',
                  onChanged: (v) => setState(() => _allowanceMinutes = v.round()),
                ),
                const SizedBox(height: AppSpacing.l),
                NumberStepperField(
                  label: 'Rolling window',
                  value: _windowHours,
                  min: 0.5,
                  max: 48,
                  step: 0.5,
                  suffix: 'hrs',
                  decimals: 1,
                  onChanged: (v) => setState(() => _windowHours = v),
                ),
                const SizedBox(height: AppSpacing.l),
                NumberStepperField(
                  label: 'Configuration lock',
                  value: _commitmentDays.toDouble(),
                  min: 1,
                  max: 365,
                  step: 1,
                  suffix: 'days',
                  onChanged: (v) => setState(() => _commitmentDays = v.round()),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.m),
          AppCard(
            child: Column(
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Block unidentified screens'),
                  subtitle: Text(
                    'Stricter, but more likely to misfire on an unrecognized screen.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  value: _blockUnknownScreens,
                  onChanged: (v) => setState(() => _blockUnknownScreens = v),
                ),
                const SizedBox(height: AppSpacing.s),
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
                  Padding(
                    padding: const EdgeInsets.only(top: AppSpacing.s),
                    child: Text(
                      'Managed mode only takes effect if this device is actually provisioned as device owner '
                      '(see README). Otherwise ReelLock falls back to standard protection and says so on the dashboard.',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(fontStyle: FontStyle.italic),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.m),
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Trusted person', style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  "Paste the trusted person's EC (P-256) public key, Base64 X.509 SubjectPublicKeyInfo. "
                  'They generate this once on their own device - see tools/trusted_signer.js.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: AppSpacing.s),
                TextField(
                  controller: _trustedKeyController,
                  maxLines: 3,
                  decoration: const InputDecoration(hintText: 'MFkwEwYHKoZIzj0CAQ...'),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.l),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.s),
              child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
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

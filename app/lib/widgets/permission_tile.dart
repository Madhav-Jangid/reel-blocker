import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import 'app_card.dart';

/// A single permission's status row: icon, title (+ "Required" marker),
/// subtitle, and either a green check or an action button. Shared between
/// `permission_setup_screen.dart` (the pre-activation gate) and
/// `settings_tab.dart` (the ongoing status recap).
class PermissionTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool granted;
  final bool required;
  final String actionLabel;
  final VoidCallback onAction;

  /// True for permissions Android gives no API to check (e.g. MIUI
  /// Autostart) - always shows the action button, never a false checkmark.
  final bool statusUnverifiable;

  const PermissionTile({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.granted,
    required this.required,
    required this.actionLabel,
    required this.onAction,
    this.statusUnverifiable = false,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final isGranted = granted && !statusUnverifiable;

    return AppCard(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 40,
            height: 40,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: isGranted ? AppSeverity.success.withValues(alpha: 0.14) : scheme.surfaceContainerHighest,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 20, color: isGranted ? AppSeverity.success : scheme.onSurfaceVariant),
          ),
          const SizedBox(width: AppSpacing.m),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(child: Text(title, style: Theme.of(context).textTheme.titleSmall)),
                    if (required) ...[
                      const SizedBox(width: AppSpacing.xs),
                      Text('Required', style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.error)),
                    ],
                  ],
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.s),
          if (isGranted)
            const Icon(Icons.check_circle, color: AppSeverity.success)
          else
            OutlinedButton(onPressed: onAction, child: Text(actionLabel)),
        ],
      ),
    );
  }
}

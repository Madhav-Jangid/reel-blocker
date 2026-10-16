import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

/// Consistent rounded, padded container used in place of the bare
/// `Card(child: Padding(...))` pattern that used to be repeated in every
/// screen. Pulls shape/color from the app theme's `CardTheme` so a single
/// change there restyles every card at once.
class AppCard extends StatelessWidget {
  final Widget child;
  final Color? color;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;

  const AppCard({
    super.key,
    required this.child,
    this.color,
    this.padding = const EdgeInsets.all(AppSpacing.m),
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final cardTheme = Theme.of(context).cardTheme;
    return Material(
      color: color ?? cardTheme.color,
      shape: cardTheme.shape,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(padding: padding, child: child),
      ),
    );
  }
}

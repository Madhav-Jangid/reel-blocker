import 'package:flutter/material.dart';

/// 8pt-ish spacing scale used everywhere instead of magic EdgeInsets literals.
class AppSpacing {
  AppSpacing._();
  static const double xs = 4;
  static const double s = 8;
  static const double m = 16;
  static const double l = 24;
  static const double xl = 32;
}

/// Corner-radius scale, matched to [AppTheme]'s component themes.
class AppRadius {
  AppRadius._();
  static const double card = 20;
  static const double control = 14;
  static const double pill = 100;
}

/// Single source of visual truth for the whole app - every screen pulls its
/// styling from `Theme.of(context)` (card shape, button shape, input
/// decoration, nav bar) rather than styling individual widgets inline.
class AppTheme {
  AppTheme._();

  static const _seed = Color(0xFF7C3AED);

  static ThemeData get light => _build(Brightness.light);
  static ThemeData get dark => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    final colorScheme = ColorScheme.fromSeed(seedColor: _seed, brightness: brightness);
    final textTheme = _textTheme(colorScheme);

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: colorScheme.surface,
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: colorScheme.surface,
        foregroundColor: colorScheme.onSurface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: colorScheme.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.card)),
        margin: EdgeInsets.zero,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.control)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          // Size.fromHeight(48) sets width: double.infinity, which crashes
          // (BoxConstraints forces an infinite width) the moment an
          // OutlinedButton sits inside a Row without Expanded - e.g.
          // PermissionTile's "Allow"/"Open settings" button. A finite
          // minimum avoids that while still giving a comfortable tap target.
          minimumSize: const Size(64, 48),
          side: BorderSide(color: colorScheme.outlineVariant),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.control)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.control)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: colorScheme.surfaceContainerHighest,
        isDense: true,
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.m, vertical: AppSpacing.m),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.control),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.control),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.control),
          borderSide: BorderSide(color: colorScheme.primary, width: 1.5),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: colorScheme.surfaceContainer,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        indicatorColor: colorScheme.primaryContainer,
        labelTextStyle: WidgetStateProperty.all(textTheme.labelMedium),
      ),
      dividerTheme: DividerThemeData(color: colorScheme.outlineVariant, space: 1),
      switchTheme: SwitchThemeData(
        trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadius.control)),
      ),
    );
  }

  static TextTheme _textTheme(ColorScheme scheme) {
    final base = Typography.material2021(platform: TargetPlatform.android).black;
    return base.copyWith(
      headlineSmall: base.headlineSmall?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.5),
      titleLarge: base.titleLarge?.copyWith(fontWeight: FontWeight.w700),
      titleMedium: base.titleMedium?.copyWith(fontWeight: FontWeight.w600),
      titleSmall: base.titleSmall?.copyWith(fontWeight: FontWeight.w600),
      labelLarge: base.labelLarge?.copyWith(fontWeight: FontWeight.w600),
      bodyMedium: base.bodyMedium?.copyWith(color: scheme.onSurfaceVariant, height: 1.4),
      bodySmall: base.bodySmall?.copyWith(color: scheme.onSurfaceVariant, height: 1.35),
    );
  }
}

/// Semantic severity colors not covered by Material's ColorScheme (which
/// only has error, not warning/success) - used by [BudgetGauge] and status pills.
class AppSeverity {
  AppSeverity._();
  static const Color warning = Color(0xFFE8A33D);
  static const Color success = Color(0xFF3DBE6C);
}

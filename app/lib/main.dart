import 'package:flutter/material.dart';
import 'screens/root_screen.dart';
import 'theme/app_theme.dart';

void main() {
  runApp(const ReelLockApp());
}

class ReelLockApp extends StatelessWidget {
  const ReelLockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ReelLock',
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      home: const RootScreen(),
    );
  }
}

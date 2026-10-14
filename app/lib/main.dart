import 'package:flutter/material.dart';
import 'screens/root_screen.dart';

void main() {
  runApp(const ReelLockApp());
}

class ReelLockApp extends StatelessWidget {
  const ReelLockApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ReelLock',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF7C3AED)),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF7C3AED), brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const RootScreen(),
    );
  }
}

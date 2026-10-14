import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';

/// need.md P2: "a diagnostic screen that records classifications without
/// collecting message contents." Shows the RAW resource-ids ScreenClassifier
/// saw on the current Instagram screen - never text or content descriptions,
/// which could carry captions/usernames/DM content (see
/// ScreenClassifier.dumpResourceIds). Point of this screen: Instagram
/// changes its UI without notice, so InstagramUiSignatures.kt has to be
/// calibrated against whatever is actually installed on a real device -
/// this is how you read that off directly.
class DiagnosticsScreen extends StatefulWidget {
  const DiagnosticsScreen({super.key});

  @override
  State<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class _DiagnosticsScreenState extends State<DiagnosticsScreen> {
  StreamSubscription<Map<String, dynamic>>? _sub;
  List<String> _lastResourceIds = [];
  String? _lastClassification;
  DateTime? _lastUpdate;

  @override
  void initState() {
    super.initState();
    _sub = NativeBridge.instance.events.listen(
      (event) {
        if (!mounted) return;
        setState(() {
          _lastUpdate = DateTime.now();
          if (event['type'] == 'diagnostics') {
            _lastResourceIds = List<String>.from(event['resourceIds'] as List);
          } else if (event['type'] == 'classification') {
            _lastClassification = '${event['screenType']} (${event['confidence']})';
          }
        });
      },
      onError: (_) {},
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Diagnostics')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Open Instagram and move between Reels, feed, DMs, profile and search. '
              'This updates live with exactly what the classifier sees - no text, only resource-ids.',
            ),
            const SizedBox(height: 12),
            Text('Last classification: ${_lastClassification ?? "(none yet)"}', style: Theme.of(context).textTheme.titleMedium),
            if (_lastUpdate != null) Text('Updated ${DateTime.now().difference(_lastUpdate!).inSeconds}s ago', style: const TextStyle(fontSize: 12, color: Colors.grey)),
            const SizedBox(height: 12),
            Text('${_lastResourceIds.length} resource-ids on screen:', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            Expanded(
              child: _lastResourceIds.isEmpty
                  ? const Center(child: Text('Nothing seen yet - open Instagram.'))
                  : ListView.builder(
                      itemCount: _lastResourceIds.length,
                      itemBuilder: (context, index) => Text(
                        _lastResourceIds[index],
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

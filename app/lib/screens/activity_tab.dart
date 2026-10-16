import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';
import '../theme/app_theme.dart';

/// Activity tab content (no own Scaffold - [HomeShell] owns that, this just
/// provides the body + an internal TabBar). Merges what used to be two
/// separate pushed screens:
///  - "Live": need.md P2's diagnostic classification feed (raw resource-ids
///    ScreenClassifier saw, never text/content-description - see
///    ScreenClassifier.dumpResourceIds for why).
///  - "History": the append-only SecurityEvent audit trail (R9 "transparent
///    failure handling").
class ActivityTab extends StatefulWidget {
  const ActivityTab({super.key});

  @override
  State<ActivityTab> createState() => _ActivityTabState();
}

class _ActivityTabState extends State<ActivityTab> with SingleTickerProviderStateMixin {
  late final TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Live'),
            Tab(text: 'History'),
          ],
        ),
        Expanded(
          child: TabBarView(
            controller: _tabController,
            children: const [_LiveClassificationView(), _SecurityHistoryView()],
          ),
        ),
      ],
    );
  }
}

class _LiveClassificationView extends StatefulWidget {
  const _LiveClassificationView();

  @override
  State<_LiveClassificationView> createState() => _LiveClassificationViewState();
}

class _LiveClassificationViewState extends State<_LiveClassificationView> {
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
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.m),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Open a monitored app (Instagram or YouTube) and move between its short-video tab and everything else. '
            'This updates live with exactly what the classifier sees - no text, only resource-ids.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: AppSpacing.m),
          Text('Last classification: ${_lastClassification ?? "(none yet)"}', style: Theme.of(context).textTheme.titleSmall),
          if (_lastUpdate != null)
            Text('Updated ${DateTime.now().difference(_lastUpdate!).inSeconds}s ago', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: AppSpacing.m),
          Text('${_lastResourceIds.length} resource-ids on screen:', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: AppSpacing.s),
          Expanded(
            child: _lastResourceIds.isEmpty
                ? Center(child: Text('Nothing seen yet - open a monitored app.', style: Theme.of(context).textTheme.bodySmall))
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
    );
  }
}

class _SecurityHistoryView extends StatefulWidget {
  const _SecurityHistoryView();

  @override
  State<_SecurityHistoryView> createState() => _SecurityHistoryViewState();
}

class _SecurityHistoryViewState extends State<_SecurityHistoryView> {
  late Future<List<SecurityEvent>> _future;

  @override
  void initState() {
    super.initState();
    _future = NativeBridge.instance.getSecurityEvents().then((list) => list.map(SecurityEvent.fromMap).toList());
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<SecurityEvent>>(
      future: _future,
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
        final events = snapshot.data!;
        if (events.isEmpty) {
          return Center(child: Text('No events yet', style: Theme.of(context).textTheme.bodyMedium));
        }
        return ListView.separated(
          itemCount: events.length,
          separatorBuilder: (_, _) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final event = events[index];
            final time = DateTime.fromMillisecondsSinceEpoch(event.timestampEpochMillis);
            return ListTile(
              title: Text(event.type),
              subtitle: Text('${event.outcome}${event.details != null ? ' - ${event.details}' : ''}'),
              trailing: Text(
                '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}\n'
                '${time.year}-${time.month.toString().padLeft(2, '0')}-${time.day.toString().padLeft(2, '0')}',
                style: const TextStyle(fontSize: 11),
              ),
            );
          },
        );
      },
    );
  }
}

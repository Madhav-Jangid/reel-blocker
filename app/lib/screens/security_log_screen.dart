import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../models/commitment_models.dart';

/// Read-only view of SecurityEvent (persistence/Entities.kt) - append-only
/// audit trail, R9 "transparent failure handling".
class SecurityLogScreen extends StatefulWidget {
  const SecurityLogScreen({super.key});

  @override
  State<SecurityLogScreen> createState() => _SecurityLogScreenState();
}

class _SecurityLogScreenState extends State<SecurityLogScreen> {
  late Future<List<SecurityEvent>> _future;

  @override
  void initState() {
    super.initState();
    _future = NativeBridge.instance.getSecurityEvents().then((list) => list.map(SecurityEvent.fromMap).toList());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Security log')),
      body: FutureBuilder<List<SecurityEvent>>(
        future: _future,
        builder: (context, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          final events = snapshot.data!;
          if (events.isEmpty) return const Center(child: Text('No events yet'));
          return ListView.separated(
            itemCount: events.length,
            separatorBuilder: (_, _) => const Divider(height: 1),
            itemBuilder: (context, index) {
              final event = events[index];
              final time = DateTime.fromMillisecondsSinceEpoch(event.timestampEpochMillis);
              return ListTile(
                title: Text(event.type),
                subtitle: Text('${event.outcome}${event.details != null ? ' - ${event.details}' : ''}'),
                trailing: Text('${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}\n${time.year}-${time.month.toString().padLeft(2, '0')}-${time.day.toString().padLeft(2, '0')}', style: const TextStyle(fontSize: 11)),
                isThreeLine: false,
              );
            },
          );
        },
      ),
    );
  }
}

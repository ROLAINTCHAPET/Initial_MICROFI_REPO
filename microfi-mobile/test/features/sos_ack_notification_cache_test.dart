import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:microfi_mobile/features/emergency/sos_ack_notification_cache.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('SosAckNotificationCache', () {
    test('the very first check for an agent reports nothing new, even if already-acknowledged ids exist', () async {
      final cache = SosAckNotificationCache('agent-1');
      final newlyAcknowledged = await cache.diffNewlyAcknowledged(['sos-old-1', 'sos-old-2']);

      expect(newlyAcknowledged, isEmpty);
    });

    test('a subsequent check reports only ids that are genuinely new since the last check', () async {
      final cache = SosAckNotificationCache('agent-1');
      await cache.diffNewlyAcknowledged(['sos-old-1']);

      final newlyAcknowledged = await cache.diffNewlyAcknowledged(['sos-old-1', 'sos-new-1']);

      expect(newlyAcknowledged, ['sos-new-1']);
    });

    test('reports nothing new when nothing has changed', () async {
      final cache = SosAckNotificationCache('agent-1');
      await cache.diffNewlyAcknowledged(['sos-a']);

      final newlyAcknowledged = await cache.diffNewlyAcknowledged(['sos-a']);

      expect(newlyAcknowledged, isEmpty);
    });

    test('survives being checked again with the same set (simulating a widget rebuild) without re-reporting', () async {
      final cache = SosAckNotificationCache('agent-1');
      await cache.diffNewlyAcknowledged(['sos-a']);
      await cache.diffNewlyAcknowledged(['sos-a', 'sos-b']); // sos-b newly seen here

      // A fresh instance (simulating HomeScreen being rebuilt on a tab switch) checking again
      // with the same data must not re-announce sos-b a second time.
      final rebuiltInstance = SosAckNotificationCache('agent-1');
      final newlyAcknowledged = await rebuiltInstance.diffNewlyAcknowledged(['sos-a', 'sos-b']);

      expect(newlyAcknowledged, isEmpty);
    });

    test('keeps independent state per agent id', () async {
      final agentA = SosAckNotificationCache('agent-a');
      final agentB = SosAckNotificationCache('agent-b');
      await agentA.diffNewlyAcknowledged(['sos-a1']);
      await agentB.diffNewlyAcknowledged(['sos-b1']);

      final newForA = await agentA.diffNewlyAcknowledged(['sos-a1', 'sos-a2']);
      final newForB = await agentB.diffNewlyAcknowledged(['sos-b1']);

      expect(newForA, ['sos-a2']);
      expect(newForB, isEmpty);
    });
  });
}

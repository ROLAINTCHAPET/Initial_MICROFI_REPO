import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:microfi_mobile/features/home/collection_rejection_notification_cache.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('CollectionRejectionNotificationCache', () {
    test('the very first check for an agent reports nothing new, even if already-decided ids exist', () async {
      final cache = CollectionRejectionNotificationCache('agent-1');
      final newlyDecided = await cache.diffNewlyDecided(['req-old-1', 'req-old-2']);

      expect(newlyDecided, isEmpty);
    });

    test('a subsequent check reports only ids that are genuinely new since the last check', () async {
      final cache = CollectionRejectionNotificationCache('agent-1');
      await cache.diffNewlyDecided(['req-old-1']);

      final newlyDecided = await cache.diffNewlyDecided(['req-old-1', 'req-new-1']);

      expect(newlyDecided, ['req-new-1']);
    });

    test('reports nothing new when nothing has changed', () async {
      final cache = CollectionRejectionNotificationCache('agent-1');
      await cache.diffNewlyDecided(['req-a']);

      final newlyDecided = await cache.diffNewlyDecided(['req-a']);

      expect(newlyDecided, isEmpty);
    });

    test('survives being checked again with the same set (simulating a widget rebuild) without re-reporting', () async {
      final cache = CollectionRejectionNotificationCache('agent-1');
      await cache.diffNewlyDecided(['req-a']);
      await cache.diffNewlyDecided(['req-a', 'req-b']); // req-b newly seen here

      final rebuiltInstance = CollectionRejectionNotificationCache('agent-1');
      final newlyDecided = await rebuiltInstance.diffNewlyDecided(['req-a', 'req-b']);

      expect(newlyDecided, isEmpty);
    });

    test('keeps independent state per agent id', () async {
      final agentA = CollectionRejectionNotificationCache('agent-a');
      final agentB = CollectionRejectionNotificationCache('agent-b');
      await agentA.diffNewlyDecided(['req-a1']);
      await agentB.diffNewlyDecided(['req-b1']);

      final newForA = await agentA.diffNewlyDecided(['req-a1', 'req-a2']);
      final newForB = await agentB.diffNewlyDecided(['req-b1']);

      expect(newForA, ['req-a2']);
      expect(newForB, isEmpty);
    });
  });
}

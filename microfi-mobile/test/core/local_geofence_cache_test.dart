import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/local_geofence_cache.dart';
import 'package:microfi_mobile/features/home/agent_profile.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalGeofenceCache', () {
    test('returns null when nothing has been saved yet', () async {
      expect(await LocalGeofenceCache('agent-1').read(), isNull);
    });

    test('round-trips a saved geofence', () async {
      final cache = LocalGeofenceCache('agent-1');
      await cache.save(AgentGeofence(vertices: [
        GeofenceVertex(lat: 0, lon: 0),
        GeofenceVertex(lat: 0, lon: 10),
        GeofenceVertex(lat: 10, lon: 10),
      ]));

      final snapshot = await cache.read();
      expect(snapshot, isNotNull);
      expect(snapshot!.vertices, hasLength(3));
      expect(snapshot.vertices[1].lon, 10);
      expect(snapshot.isUnrestricted, isFalse);
    });

    test('round-trips an empty (unassigned) geofence', () async {
      final cache = LocalGeofenceCache('agent-1');
      await cache.save(AgentGeofence(vertices: []));

      final snapshot = await cache.read();
      expect(snapshot!.vertices, isEmpty);
      expect(snapshot.isUnrestricted, isTrue);
    });

    test('a later save overwrites the earlier snapshot', () async {
      final cache = LocalGeofenceCache('agent-1');
      await cache.save(AgentGeofence(vertices: [GeofenceVertex(lat: 0, lon: 0)]));
      await cache.save(AgentGeofence(vertices: []));

      expect((await cache.read())!.isUnrestricted, isTrue);
    });

    test('keeps independent snapshots per agent id', () async {
      await LocalGeofenceCache('agent-a').save(AgentGeofence(vertices: [GeofenceVertex(lat: 1, lon: 1)]));
      await LocalGeofenceCache('agent-b').save(AgentGeofence(vertices: []));

      expect((await LocalGeofenceCache('agent-a').read())!.isUnrestricted, isFalse);
      expect((await LocalGeofenceCache('agent-b').read())!.isUnrestricted, isTrue);
    });

    test("clear() removes the snapshot without touching another agent's", () async {
      final agentA = LocalGeofenceCache('agent-a');
      final agentB = LocalGeofenceCache('agent-b');
      await agentA.save(AgentGeofence(vertices: [GeofenceVertex(lat: 1, lon: 1)]));
      await agentB.save(AgentGeofence(vertices: [GeofenceVertex(lat: 2, lon: 2)]));

      await agentA.clear();

      expect(await agentA.read(), isNull);
      expect((await agentB.read())!.vertices, hasLength(1));
    });
  });
}

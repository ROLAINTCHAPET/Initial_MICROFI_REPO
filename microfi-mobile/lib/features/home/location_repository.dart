import '../../core/api_client.dart';

/// UC-10 — periodic GPS position reporting (POST /agents/{id}/location). Feeds both the
/// Back-Office's historical route view and server-side geofence breach evaluation.
class LocationRepository {
  final String token;

  LocationRepository(this.token);

  Future<void> sendPing(String agentId, double lat, double lon) async {
    final client = ApiClient(token: token);
    await client.post('/agents/$agentId/location', {'lat': lat, 'lon': lon});
  }
}

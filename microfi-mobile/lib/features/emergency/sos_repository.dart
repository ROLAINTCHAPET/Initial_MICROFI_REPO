import '../../core/api_client.dart';

class SosStatus {
  final String id;
  final DateTime raisedAt;
  final DateTime? acknowledgedAt;

  SosStatus({required this.id, required this.raisedAt, this.acknowledgedAt});

  bool get acknowledged => acknowledgedAt != null;

  factory SosStatus.fromJson(Map<String, dynamic> json) => SosStatus(
        id: json['id'] as String,
        raisedAt: DateTime.parse(json['raisedAt'] as String),
        acknowledgedAt: json['acknowledgedAt'] != null ? DateTime.parse(json['acknowledgedAt'] as String) : null,
      );
}

/// UC-14 — Emergency SOS. Never gated on GPS: lat/lon are best-effort, so a distress signal can
/// never be blocked by a missing location fix (matches SosRequest server-side).
class SosRepository {
  final String token;
  final String agentId;

  SosRepository(this.token, this.agentId);

  Future<void> raise({double? lat, double? lon}) async {
    final client = ApiClient(token: token);
    await client.post('/agents/$agentId/sos', {
      if (lat != null) 'lat': lat,
      if (lon != null) 'lon': lon,
    });
  }

  /// The agent's own alerts, most recent first, including whether Back-Office has acknowledged
  /// each one — otherwise raising an SOS is a shout into the void with no way to know it landed.
  Future<List<SosStatus>> listMine() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/sos') as List<dynamic>;
    return json.map((e) => SosStatus.fromJson(e as Map<String, dynamic>)).toList();
  }
}

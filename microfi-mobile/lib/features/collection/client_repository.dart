import '../../core/api_client.dart';
import 'client.dart';

/// UC-06 — Multi-Channel Client Lookup (GET /clients/lookup). Search by membership number,
/// phone, or name; the endpoint returns candidates for the agent to disambiguate (FR-06).
class ClientRepository {
  final String token;

  ClientRepository(this.token);

  /// Blank/whitespace-only query returns every client (not branch-scoped — which clients an agent
  /// may actually collect from is governed by their assigned geofence at collection time), so the
  /// search screen can list available clients before the agent types anything.
  Future<List<Client>> search(String query) async {
    final trimmed = query.trim();
    final client = ApiClient(token: token);
    final path = trimmed.isEmpty ? '/clients/lookup' : '/clients/lookup?query=${Uri.encodeQueryComponent(trimmed)}';
    final json = await client.get(path) as List<dynamic>;
    return json.map((e) => Client.fromJson(e as Map<String, dynamic>)).toList();
  }
}

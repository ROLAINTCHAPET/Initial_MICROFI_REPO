import '../../core/api_client.dart';
import 'agent_profile.dart';

class HomeRepository {
  final String token;

  HomeRepository(this.token);

  ApiClient get _client => ApiClient(token: token);

  /// Resolves the caller's own profile — the id this returns is what unlocks every other
  /// agent-facing lookup (escrow, route, geofence), since the JWT itself only carries the
  /// employee code.
  Future<AgentProfile> fetchMyProfile() async {
    final json = await _client.get('/agents/me') as Map<String, dynamic>;
    return AgentProfile.fromJson(json);
  }

  Future<EscrowStatus> fetchEscrow(String agentId) async {
    final json = await _client.get('/agents/$agentId/escrow') as Map<String, dynamic>;
    return EscrowStatus.fromJson(json);
  }

  /// Replaces the transaction PIN — both the mandatory first-time replacement of the
  /// admin-assigned starting PIN and any later voluntary change go through this same call.
  Future<AgentProfile> changePin({required String currentPin, required String newPin}) async {
    final json = await _client.patch('/agents/me/pin', {
      'currentPin': currentPin,
      'newPin': newPin,
    });
    return AgentProfile.fromJson(json);
  }
}

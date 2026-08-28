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

  /// Tells the server how many collections are still queued locally, unsynced — the only way it
  /// can know, since a collection that hasn't reached it yet is otherwise invisible. This is what
  /// actually lets OfjService's end-of-day close-blocking gate work: without it, a branch's
  /// session could close (and export) while an agent still has real cash sitting on their phone.
  Future<void> reportSyncStatus(String agentId, int pendingCount) async {
    await _client.patchNoContent('/agents/$agentId/sync-status', {
      'pendingCount': pendingCount,
    });
  }
}

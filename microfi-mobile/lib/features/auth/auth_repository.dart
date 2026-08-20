import '../../core/api_client.dart';

/// UC-01 — Agent Login. Mirrors AuthenticationController#login exactly: username, password, and
/// the device's IMEI. IMEI is only enforced server-side if the agent has one bound on their
/// account (BR-Auth-02) — a blank value here is fine for agents enrolled without device binding.
/// The transaction PIN is never part of login — see CollectionRepository for where it's checked.
class AuthRepository {
  final ApiClient _client = ApiClient();

  Future<String> login({
    required String username,
    required String password,
    required String imei,
  }) async {
    final response = await _client.post('/auth/agent/login', {
      'username': username,
      'password': password,
      'imei': imei,
    });
    return response['token'] as String;
  }
}

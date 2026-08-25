import '../../core/api_client.dart';
import 'client_models.dart';

/// UC-19/20/21/22 — client digital-booklet self-activation, login, and read-only self-service.
/// Mirrors ClientAuthenticationController/ClientSelfServiceController exactly.
class ClientAuthRepository {
  final ApiClient _client = ApiClient();

  Future<String> login({required String login, required String pin}) async {
    final response = await _client.post('/auth/client/login', {'login': login, 'pin': pin});
    return response['token'] as String;
  }

  /// Step 1 of 2: sets the client's own login/PIN. Returns the confirmation message — the booklet
  /// token isn't issued until an agent also sponsors it and the client confirms payment.
  Future<String> activate({required String activationId, required String login, required String pin}) async {
    final response = await _client.post('/auth/client/activate', {
      'activationId': activationId,
      'login': login,
      'pin': pin,
    });
    return response['message'] as String;
  }
}

class ClientSelfRepository {
  final String token;

  ClientSelfRepository(this.token);

  ApiClient get _client => ApiClient(token: token);

  Future<ClientSelfProfile> fetchProfile() async {
    final json = await _client.get('/clients/me/profile') as Map<String, dynamic>;
    return ClientSelfProfile.fromJson(json);
  }

  /// Not called from any screen right now — balance display was pulled from the UI since it isn't
  /// backed by a real CBS yet (see client_home_screen.dart/client_wallet_screen.dart). Kept here,
  /// not deleted, since the endpoint itself is real and this is expected to come back once a real
  /// CBS integration lands.
  Future<ClientBalance> fetchBalance() async {
    final json = await _client.get('/clients/me/balance') as Map<String, dynamic>;
    return ClientBalance.fromJson(json);
  }

  Future<List<ClientHistoryEntry>> fetchHistory() async {
    final json = await _client.get('/clients/me/history') as List<dynamic>;
    return json.map((e) => ClientHistoryEntry.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Visible immediately after an agent validates a collection, unlike [fetchHistory] (CBS-backed,
  /// only catches up at end-of-day export) — see ClientSelfServiceController#recentCollections.
  Future<List<ClientRecentCollection>> fetchRecentCollections() async {
    final json = await _client.get('/clients/me/recent-collections') as List<dynamic>;
    return json.map((e) => ClientRecentCollection.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// UC-19 step 3: the client's own half of the two-party activation gate — re-enters their PIN
  /// to confirm the agent's cash-receipt record is correct (BR-04). Safe to call even if the
  /// agent hasn't sponsored yet; the response's status just comes back AWAITING_SPONSORSHIP.
  Future<ClientActivationConfirmation> confirmActivationPayment(String pin) async {
    final json = await _client.post('/clients/me/activation/pay', {'pin': pin});
    return ClientActivationConfirmation.fromJson(json);
  }
}

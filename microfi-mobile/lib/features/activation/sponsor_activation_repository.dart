import '../../core/api_client.dart';

class PendingClientActivation {
  final String id;
  final String mfiMemberNo;
  final String fullName;
  final String phone;
  final bool sponsored;

  PendingClientActivation({
    required this.id,
    required this.mfiMemberNo,
    required this.fullName,
    required this.phone,
    required this.sponsored,
  });

  factory PendingClientActivation.fromJson(Map<String, dynamic> json) => PendingClientActivation(
        id: json['id'] as String,
        mfiMemberNo: json['mfiMemberNo'] as String,
        fullName: json['fullName'] as String,
        phone: json['phone'] as String? ?? '',
        sponsored: json['sponsored'] as bool? ?? false,
      );
}

class ClientActivationResult {
  final String clientId;
  final String status;

  ClientActivationResult({required this.clientId, required this.status});

  factory ClientActivationResult.fromJson(Map<String, dynamic> json) => ClientActivationResult(
        clientId: json['clientId'] as String,
        status: json['status'] as String,
      );
}

/// UC-19 step 2 of the two-party activation gate: the agent picks a client from the list of
/// people who've already self-activated (set their own login) but have no live booklet token yet,
/// and sponsors them by id — no need to ask the client for the login string they chose, since
/// there's a real candidate list to search and tap instead. See
/// ClientActivationService#listPendingActivation / #sponsorActivationById.
class SponsorActivationRepository {
  final String token;

  SponsorActivationRepository(this.token);

  Future<List<PendingClientActivation>> listPending(String query) async {
    final client = ApiClient(token: token);
    final path = query.trim().isEmpty
        ? '/clients/pending-activation'
        : '/clients/pending-activation?query=${Uri.encodeQueryComponent(query.trim())}';
    final json = await client.get(path) as List<dynamic>;
    return json.map((e) => PendingClientActivation.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<ClientActivationResult> sponsor(String clientId) async {
    final client = ApiClient(token: token);
    final json = await client.post('/clients/$clientId/activation', <String, dynamic>{});
    return ClientActivationResult.fromJson(json);
  }
}

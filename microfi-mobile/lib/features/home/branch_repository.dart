import '../../core/api_client.dart';

class AgentBranch {
  final String name;
  final String? phone;
  final String? openTime;
  final String? closeTime;

  AgentBranch({required this.name, required this.phone, this.openTime, this.closeTime});

  factory AgentBranch.fromJson(Map<String, dynamic> json) => AgentBranch(
        name: json['name'] as String,
        phone: json['phone'] as String?,
        openTime: json['openTime'] as String?,
        closeTime: json['closeTime'] as String?,
      );
}

/// GET /agents/me/branch — the caller's own branch, for the "Contact Branch" action.
class BranchRepository {
  final String token;

  BranchRepository(this.token);

  Future<AgentBranch> fetchMyBranch() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/branch') as Map<String, dynamic>;
    return AgentBranch.fromJson(json);
  }

  /// GET /agents/me/mfi-name — the org name printed on a receipt (BR-Notif-01), cached client-side
  /// via ReceiptContextCache so an offline collection can still compose a compliant one.
  Future<String> fetchMfiName() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/mfi-name') as Map<String, dynamic>;
    return json['name'] as String;
  }
}

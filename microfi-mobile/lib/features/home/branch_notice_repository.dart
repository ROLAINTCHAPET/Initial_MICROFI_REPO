import '../../core/api_client.dart';

class BranchNotice {
  final String id;
  final String message;
  final DateTime createdAt;

  BranchNotice({required this.id, required this.message, required this.createdAt});

  factory BranchNotice.fromJson(Map<String, dynamic> json) => BranchNotice(
        id: json['id'] as String,
        message: json['message'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

/// UC-15 — there's no push infrastructure in this app, so a same-day schedule change (the other
/// notification channel is SMS, sent alongside this) is only visible once the agent has the app
/// open; HomeScreen polls this the same way it already polls for SOS acknowledgement.
class BranchNoticeRepository {
  final String token;

  BranchNoticeRepository(this.token);

  Future<List<BranchNotice>> listMine() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/branch-notices') as List<dynamic>;
    return json.map((e) => BranchNotice.fromJson(e as Map<String, dynamic>)).toList();
  }
}

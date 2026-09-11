import '../../core/api_client.dart';

class BroadcastMessage {
  final String id;
  final String message;
  final DateTime createdAt;

  BroadcastMessage({required this.id, required this.message, required this.createdAt});

  factory BroadcastMessage.fromJson(Map<String, dynamic> json) => BroadcastMessage(
        id: json['id'] as String,
        message: json['message'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}

/// An ADMIN/BRANCH_MANAGER announcement to every agent — network-wide or this agent's own branch.
/// Same no-push-infrastructure reasoning as BranchNoticeRepository: SMS carries the urgency, this
/// is what makes it visible once the app is open, including to an agent who missed the SMS.
class BroadcastRepository {
  final String token;

  BroadcastRepository(this.token);

  Future<List<BroadcastMessage>> listMine() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/broadcasts') as List<dynamic>;
    return json.map((e) => BroadcastMessage.fromJson(e as Map<String, dynamic>)).toList();
  }
}

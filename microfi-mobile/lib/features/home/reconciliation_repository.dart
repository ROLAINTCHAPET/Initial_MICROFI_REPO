import '../../core/api_client.dart';

class PendingReconciliationLine {
  final String lineId;
  final int totalXaf;
  final int collectionCount;
  final DateTime? lastCountedAt;

  PendingReconciliationLine({
    required this.lineId,
    required this.totalXaf,
    required this.collectionCount,
    required this.lastCountedAt,
  });

  factory PendingReconciliationLine.fromJson(Map<String, dynamic> json) => PendingReconciliationLine(
        lineId: json['lineId'] as String,
        totalXaf: json['totalXaf'] as int,
        collectionCount: json['collectionCount'] as int,
        lastCountedAt: json['lastCountedAt'] != null ? DateTime.parse(json['lastCountedAt'] as String) : null,
      );
}

class CollectionRejectionRequest {
  final String id;
  final String collectionId;
  final String reason;
  final String status;
  final String? decisionReason;

  CollectionRejectionRequest({
    required this.id,
    required this.collectionId,
    required this.reason,
    required this.status,
    required this.decisionReason,
  });

  factory CollectionRejectionRequest.fromJson(Map<String, dynamic> json) => CollectionRejectionRequest(
        id: json['id'] as String,
        collectionId: json['collectionId'] as String,
        reason: json['reason'] as String,
        status: json['status'] as String,
        decisionReason: json['decisionReason'] as String?,
      );
}

/// A cashier has physically counted this cash, but it still occupies the agent's own escrow
/// ceiling until they confirm it themselves (or it auto-expires server-side) — see
/// CollectionReconciliationStatus's doc on the backend. There's no push infrastructure in this
/// app, so HomeScreen polls this the same way it already polls branch notices/SOS acknowledgement.
class ReconciliationRepository {
  final String token;

  ReconciliationRepository(this.token);

  Future<List<PendingReconciliationLine>> listMyPendingConfirmations() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/pending-confirmations') as List<dynamic>;
    return json.map((e) => PendingReconciliationLine.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> confirm(String lineId) async {
    final client = ApiClient(token: token);
    await client.postNoContent('/agents/me/reconciliations/$lineId/confirm');
  }

  Future<void> requestCollectionRejection(String collectionId, String reason) async {
    final client = ApiClient(token: token);
    await client.post('/agents/me/collections/$collectionId/reject-request', {'reason': reason});
  }

  /// Same polling pattern as everything else here — the mobile app has no way to learn a
  /// manager/admin decided this agent's void request except by asking.
  Future<List<CollectionRejectionRequest>> listMyRejectionRequests() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/collection-rejection-requests') as List<dynamic>;
    return json.map((e) => CollectionRejectionRequest.fromJson(e as Map<String, dynamic>)).toList();
  }
}

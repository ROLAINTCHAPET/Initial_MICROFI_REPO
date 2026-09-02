import '../../core/api_client.dart';

class DenominationLine {
  final int faceValueXaf;
  final int quantity;

  DenominationLine({required this.faceValueXaf, required this.quantity});

  Map<String, dynamic> toJson() => {'faceValueXaf': faceValueXaf, 'quantity': quantity};
}

class CollectionResult {
  final String id;
  final int amountXaf;
  final bool duplicate;
  final String? locationName;

  CollectionResult({required this.id, required this.amountXaf, required this.duplicate, this.locationName});

  factory CollectionResult.fromJson(Map<String, dynamic> json) => CollectionResult(
        id: json['id'] as String,
        amountXaf: (json['amountXaf'] as num).toInt(),
        duplicate: json['duplicate'] as bool? ?? false,
        locationName: json['locationName'] as String?,
      );
}

class CollectionSyncResult {
  final String deviceTxId;
  final bool success;
  final String? error;
  final String? collectionId;

  CollectionSyncResult({required this.deviceTxId, required this.success, this.error, this.collectionId});

  factory CollectionSyncResult.fromJson(Map<String, dynamic> json) => CollectionSyncResult(
        deviceTxId: json['deviceTxId'] as String,
        success: json['success'] as bool,
        error: json['error'] as String?,
        collectionId: (json['collection'] as Map<String, dynamic>?)?['id'] as String?,
      );
}

class CollectionSummary {
  final String id;
  final String? clientName;
  final int amountXaf;
  final String? locationName;
  final DateTime collectedAt;

  CollectionSummary({required this.id, required this.clientName, required this.amountXaf, this.locationName, required this.collectedAt});

  factory CollectionSummary.fromJson(Map<String, dynamic> json) => CollectionSummary(
        id: json['id'] as String,
        clientName: json['clientName'] as String?,
        amountXaf: (json['amountXaf'] as num).toInt(),
        locationName: json['locationName'] as String?,
        collectedAt: DateTime.parse(json['collectedAt'] as String),
      );
}

/// UC-06/07/08/12 — Digital Cash Desk (POST /collections). GPS-gated, denomination-checked,
/// escrow-checked server-side; idempotent on (agent, deviceTxId) so a retried submit after a
/// dropped connection can't double-count.
class CollectionRepository {
  final String token;

  CollectionRepository(this.token);

  Future<CollectionResult> record({
    required String clientId,
    required int amountXaf,
    required double lat,
    required double lon,
    double? accuracyM,
    required String deviceTxId,
    required String terminalId,
    required List<DenominationLine> denominationLines,
    required String pin,
  }) async {
    final client = ApiClient(token: token);
    final json = await client.post('/collections', {
      'clientId': clientId,
      'amountXaf': amountXaf,
      'lat': lat,
      'lon': lon,
      if (accuracyM != null) 'accuracyM': accuracyM,
      'collectedAt': DateTime.now().toUtc().toIso8601String(),
      'deviceTxId': deviceTxId,
      'terminalId': terminalId,
      'denominationLines': denominationLines.map((d) => d.toJson()).toList(),
      'pin': pin,
    });
    return CollectionResult.fromJson(json);
  }

  /// The agent's own last 50 collections, newest first (GET /collections — always self-scoped
  /// server-side, resolved from the caller's principal).
  Future<List<CollectionSummary>> listMine() async {
    final client = ApiClient(token: token);
    final json = await client.get('/collections') as List<dynamic>;
    return json.map((e) => CollectionSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// FR-07 batch offline sync (POST /collections/sync) — each item is processed independently
  /// server-side, so one rejected record (e.g. now over ceiling) doesn't block the rest.
  Future<List<CollectionSyncResult>> syncBatch(List<Map<String, dynamic>> requests) async {
    final client = ApiClient(token: token);
    final json = await client.postJson('/collections/sync', requests) as List<dynamic>;
    return json.map((e) => CollectionSyncResult.fromJson(e as Map<String, dynamic>)).toList();
  }
}

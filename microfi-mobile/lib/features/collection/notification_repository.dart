import '../../core/api_client.dart';

class NotificationResult {
  final String channel;
  final String status;
  final bool printedReceipt;
  final String? receiptText;

  NotificationResult({required this.channel, required this.status, required this.printedReceipt, this.receiptText});

  factory NotificationResult.fromJson(Map<String, dynamic> json) => NotificationResult(
        channel: json['channel'] as String,
        status: json['status'] as String,
        printedReceipt: json['printedReceipt'] as bool? ?? false,
        receiptText: json['receiptText'] as String?,
      );
}

/// UC-09 — Post-Validation Multi-Channel Notification (POST /collections/{id}/notify). Called
/// twice in the normal flow: once right after a successful collection (SMS attempt + fetch
/// receiptText for printing — printedReceipt: false), and again if the agent actually prints
/// (printedReceipt: true), so the audit log reflects what really happened either way.
class NotificationRepository {
  final String token;

  NotificationRepository(this.token);

  Future<NotificationResult> notifyCollection(String collectionId, {required bool printedReceipt}) async {
    final client = ApiClient(token: token);
    final json = await client.post('/collections/$collectionId/notify', {'printedReceipt': printedReceipt});
    return NotificationResult.fromJson(json);
  }
}

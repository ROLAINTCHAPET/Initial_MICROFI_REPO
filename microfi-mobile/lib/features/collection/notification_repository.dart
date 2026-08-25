import '../../core/api_client.dart';
import '../../core/locale_preference.dart';
import 'receipt_models.dart';

class NotificationResult {
  final String channel;
  final String status;
  final bool printedReceipt;
  final String? receiptText;
  final ReceiptData? receiptData;

  NotificationResult({
    required this.channel,
    required this.status,
    required this.printedReceipt,
    this.receiptText,
    this.receiptData,
  });

  factory NotificationResult.fromJson(Map<String, dynamic> json) => NotificationResult(
        channel: json['channel'] as String,
        status: json['status'] as String,
        printedReceipt: json['printedReceipt'] as bool? ?? false,
        receiptText: json['receiptText'] as String?,
        receiptData: json['receiptData'] != null ? ReceiptData.fromJson(json['receiptData'] as Map<String, dynamic>) : null,
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
    // Matches whatever language the agent currently has the app set to (LocalePreference), so the
    // server-composed receiptText/receiptData come back in the same language OfflineReceiptComposer
    // would have used for the same collection offline — see that class's own doc comment.
    final locale = LocalePreference.instance.notifier.value.languageCode;
    final json = await client.post('/collections/$collectionId/notify', {'printedReceipt': printedReceipt, 'locale': locale});
    return NotificationResult.fromJson(json);
  }
}

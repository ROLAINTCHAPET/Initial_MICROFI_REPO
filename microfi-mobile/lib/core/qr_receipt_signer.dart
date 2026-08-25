import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../features/collection/collection_repository.dart';
import '../features/collection/receipt_models.dart';
import 'offline_receipt_composer.dart';
import '../l10n/app_localizations.dart';

/// A collection's receipt, compact enough to round-trip through a single QR code — everything
/// OfflineReceiptComposer needs to render the exact same receipt on the client's own device,
/// after they scan it straight off the agent's screen. Works with zero connectivity on either
/// side: the agent already composed this locally (see collection_stepper_screen.dart), and the
/// client's app verifies + renders it locally too.
class QrReceiptPayload {
  final String uniqueRef;
  final String mfiName;
  final String branchName;
  final String agentEmployeeCode;
  final String agentFullName;
  final String clientMemberNo;
  final String clientFullName;
  final int amountXaf;
  final String collectedAtIso;
  final List<DenominationLine> denominationLines;
  final bool french;

  QrReceiptPayload({
    required this.uniqueRef,
    required this.mfiName,
    required this.branchName,
    required this.agentEmployeeCode,
    required this.agentFullName,
    required this.clientMemberNo,
    required this.clientFullName,
    required this.amountXaf,
    required this.collectedAtIso,
    required this.denominationLines,
    required this.french,
  });
}

enum QrVerificationFailureReason { notMicrofiReceipt, missingData, tampered }

class QrReceiptVerificationFailed implements Exception {
  final QrVerificationFailureReason reason;
  QrReceiptVerificationFailed(this.reason);

  /// Localized message for [reason] — resolved at catch time, since the only call site (the QR
  /// scan screen) already has a BuildContext.
  String message(AppLocalizations l10n) => switch (reason) {
        QrVerificationFailureReason.notMicrofiReceipt => l10n.qrErrorNotMicrofiReceipt,
        QrVerificationFailureReason.missingData => l10n.qrErrorMissingData,
        QrVerificationFailureReason.tampered => l10n.qrErrorTampered,
      };

  @override
  String toString() => reason.toString();
}

/// Signs/verifies a QrReceiptPayload with HMAC-SHA256 so a scanned QR can't be silently altered
/// (change the amount, swap the client) between the agent's screen and the client's scan, and so
/// a fabricated QR (not produced by a real MICROFI app) is rejected rather than rendered as if it
/// were a genuine receipt.
///
/// Worth being direct about what this key actually is: it's a single value compiled into every
/// install of this app (both the agent and client sides are the same app/binary, just different
/// logins), not a secret unique to any one agent or device. That makes it real protection against
/// casual tampering and hand-crafted fakes, but not against someone who reverse-engineers the
/// compiled app to extract it — a stronger design would issue each agent their own signing key
/// from the backend (the same keypair infrastructure the still-unbuilt backup-export feature
/// needs) and verify with a matching public key instead. This is the pragmatic version buildable
/// today without that infrastructure; upgrading to per-agent keys later wouldn't need to touch
/// the QR payload shape, only how it gets signed and verified.
class QrReceiptSigner {
  static const _sharedKey = 'MICROFI-QR-RECEIPT-v1-8f2a1c9e4b7d3f60a5e9c8b1d4f7a2e6';

  static String encode(QrReceiptPayload payload) {
    final canonical = _canonicalString(payload);
    final signature = Hmac(sha256, utf8.encode(_sharedKey)).convert(utf8.encode(canonical)).bytes;
    final json = _toJson(payload)..['sig'] = base64Encode(signature);
    return jsonEncode(json);
  }

  /// Throws QrReceiptVerificationFailed if the scanned content isn't well-formed JSON, is missing
  /// a field, or its signature doesn't match — never returns a payload that failed verification.
  static QrReceiptPayload decode(String scanned) {
    late Map<String, dynamic> json;
    try {
      json = jsonDecode(scanned) as Map<String, dynamic>;
    } catch (_) {
      throw QrReceiptVerificationFailed(QrVerificationFailureReason.notMicrofiReceipt);
    }

    final sig = json['sig'] as String?;
    if (sig == null) throw QrReceiptVerificationFailed(QrVerificationFailureReason.notMicrofiReceipt);

    QrReceiptPayload payload;
    try {
      payload = QrReceiptPayload(
        uniqueRef: json['tx'] as String,
        mfiName: json['mfi'] as String,
        branchName: json['br'] as String,
        agentEmployeeCode: json['aec'] as String,
        agentFullName: json['afn'] as String,
        clientMemberNo: json['cmn'] as String,
        clientFullName: json['cfn'] as String,
        amountXaf: (json['amt'] as num).toInt(),
        collectedAtIso: json['at'] as String,
        denominationLines: (json['dl'] as List<dynamic>)
            .map((e) => DenominationLine(faceValueXaf: (e[0] as num).toInt(), quantity: (e[1] as num).toInt()))
            .toList(),
        // Older (v1) QR payloads predate this field — default to English rather than fail decode.
        french: json['fr'] as bool? ?? false,
      );
    } catch (_) {
      throw QrReceiptVerificationFailed(QrVerificationFailureReason.missingData);
    }

    final expectedSignature = Hmac(sha256, utf8.encode(_sharedKey)).convert(utf8.encode(_canonicalString(payload))).bytes;
    if (base64Encode(expectedSignature) != sig) {
      throw QrReceiptVerificationFailed(QrVerificationFailureReason.tampered);
    }
    return payload;
  }

  /// Re-derives the exact same styled receipt OfflineReceiptComposer would have built, from a
  /// signature-verified payload — so a receipt reached by scanning is indistinguishable from one
  /// composed directly, not a separate, lesser rendering.
  static ReceiptData toReceiptData(QrReceiptPayload payload) {
    final collectedAtUtc = DateTime.parse(payload.collectedAtIso);
    return ReceiptData(
      branchName: payload.branchName,
      dateFormatted: OfflineReceiptComposer.formattedDate(collectedAtUtc, payload.french),
      agentEmployeeCode: payload.agentEmployeeCode,
      agentShortName: OfflineReceiptComposer.shortName(payload.agentFullName),
      clientMemberNo: payload.clientMemberNo,
      clientFullName: payload.clientFullName,
      amountXaf: payload.amountXaf,
      amountWords: OfflineReceiptComposer.amountWords(payload.amountXaf, payload.french),
      denominationLines: payload.denominationLines
          .map((d) => ReceiptDenominationLine(faceValueXaf: d.faceValueXaf, quantity: d.quantity, lineTotalXaf: d.faceValueXaf * d.quantity))
          .toList(),
      uniqueRef: payload.uniqueRef,
      signature: payload.french ? 'XAF · SCANNÉ · VÉRIFIÉ' : 'XAF · SCANNED · VERIFIED',
    );
  }

  static Map<String, dynamic> _toJson(QrReceiptPayload p) => {
        'v': 1,
        'tx': p.uniqueRef,
        'mfi': p.mfiName,
        'br': p.branchName,
        'aec': p.agentEmployeeCode,
        'afn': p.agentFullName,
        'cmn': p.clientMemberNo,
        'cfn': p.clientFullName,
        'amt': p.amountXaf,
        'at': p.collectedAtIso,
        'dl': p.denominationLines.map((d) => [d.faceValueXaf, d.quantity]).toList(),
        'fr': p.french,
      };

  /// Explicit, fixed-order delimited string rather than re-serializing to JSON for signing —
  /// avoids any dependency on map/JSON key-ordering staying stable between encode() and decode().
  static String _canonicalString(QrReceiptPayload p) {
    final lines = p.denominationLines.map((d) => '${d.faceValueXaf}:${d.quantity}').join(',');
    return '1|${p.uniqueRef}|${p.mfiName}|${p.branchName}|${p.agentEmployeeCode}|${p.agentFullName}|'
        '${p.clientMemberNo}|${p.clientFullName}|${p.amountXaf}|${p.collectedAtIso}|$lines|${p.french}';
  }
}

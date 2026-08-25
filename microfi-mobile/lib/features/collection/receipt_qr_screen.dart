import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../core/design_tokens.dart';
import '../../core/qr_receipt_signer.dart';
import '../../l10n/app_localizations.dart';

/// Held up to the client's camera so their own app can scan, verify, and keep an independent copy
/// of this exact receipt — see QrReceiptSigner for what's actually encoded and why it's signed.
class ReceiptQrScreen extends StatelessWidget {
  final QrReceiptPayload payload;

  const ReceiptQrScreen({super.key, required this.payload});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(title: Text(l10n.rqShowToClientTitle), backgroundColor: Colors.white, foregroundColor: MicrofiColors.primary),
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  l10n.rqClientAmountLine(payload.clientFullName, payload.amountXaf),
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                ),
                const SizedBox(height: 4),
                Text(
                  l10n.rqScanInstructions,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
                ),
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                    borderRadius: BorderRadius.circular(MicrofiRadius.md),
                  ),
                  child: QrImageView(
                    data: QrReceiptSigner.encode(payload),
                    version: QrVersions.auto,
                    size: 260,
                    backgroundColor: Colors.white,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  l10n.rqRefLine(payload.uniqueRef.substring(0, 8).toUpperCase()),
                  style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant, fontFamily: 'monospace'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

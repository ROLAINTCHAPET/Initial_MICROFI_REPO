import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../core/design_tokens.dart';
import '../../core/qr_receipt_signer.dart';
import 'client_receipt_view_screen.dart';
import '../../l10n/app_localizations.dart';

/// Camera scan of the QR an agent's app shows right after a collection (see
/// ReceiptQrScreen/QrReceiptSigner) — works with zero connectivity on either side, and the
/// signature check means a client only ever sees a receipt that's genuinely theirs, unaltered.
class ClientReceiptScanScreen extends StatefulWidget {
  const ClientReceiptScanScreen({super.key});

  @override
  State<ClientReceiptScanScreen> createState() => _ClientReceiptScanScreenState();
}

class _ClientReceiptScanScreenState extends State<ClientReceiptScanScreen> {
  final MobileScannerController _controller = MobileScannerController();
  bool _handled = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_handled || capture.barcodes.isEmpty) return;
    final raw = capture.barcodes.first.rawValue;
    if (raw == null) return;
    _handled = true;

    try {
      final payload = QrReceiptSigner.decode(raw);
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => ClientReceiptViewScreen(payload: payload)));
    } on QrReceiptVerificationFailed catch (e) {
      setState(() => _error = e.message(AppLocalizations.of(context)!));
    } catch (_) {
      setState(() => _error = AppLocalizations.of(context)!.crsCouldNotReadQr);
    }
  }

  void _retry() {
    setState(() {
      _error = null;
      _handled = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(title: Text(l10n.crsScanYourReceiptTitle), backgroundColor: Colors.black, foregroundColor: Colors.white),
      body: Stack(
        children: [
          MobileScanner(controller: _controller, onDetect: _onDetect),
          Align(
            alignment: Alignment.topCenter,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.6), borderRadius: BorderRadius.circular(MicrofiRadius.full)),
                child: Text(
                  l10n.crsPointCameraInstructions,
                  style: const TextStyle(color: Colors.white, fontSize: 13),
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          ),
          if (_error != null)
            Align(
              alignment: Alignment.bottomCenter,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(color: MicrofiColors.errorContainer, borderRadius: BorderRadius.circular(MicrofiRadius.md)),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.onErrorContainer, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 10),
                      FilledButton(onPressed: _retry, child: Text(l10n.crsTryAgain)),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

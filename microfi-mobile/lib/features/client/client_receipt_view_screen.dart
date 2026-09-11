import 'package:flutter/material.dart';

import '../../core/design_tokens.dart';
import '../../core/qr_receipt_signer.dart';
import '../../core/receipt_file_service.dart';
import '../../l10n/app_localizations.dart';

/// Shown right after a successful scan (see ClientReceiptScanScreen) — the client's own,
/// independently-held copy of the receipt, reconstructed from a signature-verified QR payload.
class ClientReceiptViewScreen extends StatefulWidget {
  final QrReceiptPayload payload;

  const ClientReceiptViewScreen({super.key, required this.payload});

  @override
  State<ClientReceiptViewScreen> createState() => _ClientReceiptViewScreenState();
}

class _ClientReceiptViewScreenState extends State<ClientReceiptViewScreen> {
  final ReceiptFileService _receiptFileService = ReceiptFileService();
  bool _downloading = false;
  bool _downloaded = false;
  String? _downloadError;

  Future<void> _download() async {
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _downloading = true;
      _downloadError = null;
    });
    try {
      final receiptData = QrReceiptSigner.toReceiptData(widget.payload);
      await _receiptFileService.downloadReceipt(receiptData, fileNameHint: widget.payload.uniqueRef.substring(0, 8), l10n: l10n);
      if (!mounted) return;
      setState(() => _downloaded = true);
    } on ReceiptFileDownloadFailed catch (e) {
      if (!mounted) return;
      setState(() {
        _downloaded = true;
        _downloadError = l10n.csReceiptSavedCouldNotOpen(e.message);
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _downloadError = l10n.csCouldNotDownloadReceipt);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final p = widget.payload;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.crvYourReceiptTitle)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(MicrofiSpacing.page),
          children: [
            Container(
              padding: const EdgeInsets.all(22),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [MicrofiColors.secondary, Color(0xFF00483C)],
                ),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle),
                    child: const Icon(Icons.check_circle_rounded, color: Colors.white, size: 32),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    l10n.amountXaf(_fmt(p.amountXaf)),
                    style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: Colors.white),
                  ),
                  Text(l10n.crvVerifiedDepositReceipt, style: TextStyle(fontSize: 13, color: Colors.white.withValues(alpha: 0.85))),
                ],
              ),
            ),
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: MicrofiColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _ReceiptRow(label: l10n.crvClientLabel, value: p.clientFullName),
                  _ReceiptRow(label: l10n.crvMemberNoLabel, value: p.clientMemberNo),
                  _ReceiptRow(label: l10n.rpAgentLabel, value: '${p.agentFullName} (${p.agentEmployeeCode})'),
                  _ReceiptRow(label: l10n.rpBranchLabel, value: p.branchName),
                  _ReceiptRow(label: l10n.rpDateLabel, value: p.collectedAtIso),
                  _ReceiptRow(label: l10n.crvReferenceLabel, value: p.uniqueRef.substring(0, 8).toUpperCase()),
                  const Divider(color: MicrofiColors.outlineVariant, height: 24),
                  Text(l10n.csDenominationBreakdown, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                  const SizedBox(height: 6),
                  ...p.denominationLines.where((d) => d.quantity > 0).map(
                        (d) => Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(l10n.crvDenominationLine(_fmt(d.faceValueXaf), d.quantity), style: const TextStyle(color: MicrofiColors.onSurfaceVariant)),
                              Text(l10n.amountXaf(_fmt(d.faceValueXaf * d.quantity)), style: const TextStyle(fontWeight: FontWeight.w700)),
                            ],
                          ),
                        ),
                      ),
                ],
              ),
            ),
            const SizedBox(height: MicrofiSpacing.gapLg),
            SizedBox(
              width: double.infinity,
              height: 44,
              child: OutlinedButton.icon(
                onPressed: _downloading ? null : _download,
                icon: Icon(_downloaded ? Icons.check_circle : Icons.download, size: 18),
                label: Text(_downloaded ? l10n.csReceiptDownloaded : (_downloading ? l10n.csDownloading : l10n.csDownloadReceipt)),
              ),
            ),
            if (_downloadError != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(_downloadError!, style: const TextStyle(color: MicrofiColors.error, fontSize: 12)),
              ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              height: 44,
              child: FilledButton(
                onPressed: () => Navigator.of(context).popUntil((r) => r.isFirst),
                child: Text(l10n.commonDone),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _fmt(int value) {
    final s = value.toString();
    final buffer = StringBuffer();
    for (int i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buffer.write(',');
      buffer.write(s[i]);
    }
    return buffer.toString();
  }
}

class _ReceiptRow extends StatelessWidget {
  final String label;
  final String value;

  const _ReceiptRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          Flexible(child: Text(value, textAlign: TextAlign.right, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
        ],
      ),
    );
  }
}

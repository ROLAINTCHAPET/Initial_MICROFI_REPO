import 'dart:typed_data';

import 'package:flutter/services.dart' show rootBundle;
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../features/collection/receipt_models.dart';
import '../l10n/app_localizations.dart';

/// Renders the downloadable PDF receipt matching Graphical Design/microfi_receipt_design.html —
/// a styled card (torn edges, denomination table, pseudo-QR), distinct from the plain-text
/// receiptText used for Bluetooth thermal printing. Uses the app's own bundled Inter font (already
/// shipped for the rest of the UI, see pubspec.yaml) rather than the design's JetBrains-Mono-styled
/// body text or PDF Base-14 fonts — Base-14/WinAnsi fonts can't reliably render accented CEMAC
/// names (e.g. "Yaoundé"), and pulling a web font at generation time would break the app's
/// offline-first design. Visual fidelity to the mockup's exact typeface is traded for correctness.
class ReceiptPdfBuilder {
  static const _paper = PdfColor.fromInt(0xFFF8F7F3);
  static const _ink = PdfColor.fromInt(0xFF20221F);
  static const _inkSoft = PdfColor.fromInt(0xFF6B6D66);
  static const _inkFaint = PdfColor.fromInt(0xFF9B9D95);
  static const _rule = PdfColor.fromInt(0xFFD8D6CC);
  static const _navy = PdfColor.fromInt(0xFF122A4E);
  static const _navySoft = PdfColor.fromInt(0xFF3A527A);
  static const _green = PdfColor.fromInt(0xFF1F6B45);
  static const _greenBg = PdfColor.fromInt(0xFFE4F0E8);
  static const _pillBg = PdfColor.fromInt(0xFFEFEDE4);

  static const _pageWidth = 400.0;
  static const _cardMargin = 20.0;
  static const _cardPaddingH = 24.0;
  static const _contentWidth = _pageWidth - _cardMargin * 2 - _cardPaddingH * 2;

  static Future<Uint8List> build(ReceiptData data, AppLocalizations l10n) async {
    final regular = pw.Font.ttf(await rootBundle.load('assets/fonts/Inter-400.ttf'));
    final medium = pw.Font.ttf(await rootBundle.load('assets/fonts/Inter-500.ttf'));
    final semibold = pw.Font.ttf(await rootBundle.load('assets/fonts/Inter-600.ttf'));
    final bold = pw.Font.ttf(await rootBundle.load('assets/fonts/Inter-700.ttf'));

    final doc = pw.Document();
    doc.addPage(
      pw.MultiPage(
        // Sized to the card's actual content (~860pt) plus headroom for longer branch/client
        // names wrapping to an extra line; MultiPage spills to a second page rather than
        // crashing if a real one ever runs longer than that.
        pageFormat: const PdfPageFormat(_pageWidth, 950, marginAll: 0),
        build: (context) => [_card(data, l10n, regular: regular, medium: medium, semibold: semibold, bold: bold)],
      ),
    );
    return doc.save();
  }

  static pw.Widget _card(
    ReceiptData data,
    AppLocalizations l10n, {
    required pw.Font regular,
    required pw.Font medium,
    required pw.Font semibold,
    required pw.Font bold,
  }) {
    return pw.Container(
      margin: const pw.EdgeInsets.all(_cardMargin),
      padding: const pw.EdgeInsets.fromLTRB(_cardPaddingH, 30, _cardPaddingH, 26),
      decoration: const pw.BoxDecoration(color: _paper),
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.center,
        children: [
          pw.Text('MICROFI',
              style: pw.TextStyle(font: bold, fontSize: 20, letterSpacing: 3, color: _navy)),
          pw.SizedBox(height: 4),
          pw.Text(l10n.rpTagline,
              style: pw.TextStyle(font: medium, fontSize: 8.5, letterSpacing: 1.6, color: _inkSoft)),
          _rule2(color: _ink, width: 1.4, top: 16, bottom: 16),
          _metaRow(l10n.rpBranchLabel, data.branchName, medium, semibold),
          pw.SizedBox(height: 4),
          _metaRow(l10n.rpDateLabel, data.dateFormatted, medium, semibold),
          pw.SizedBox(height: 4),
          _metaRow(l10n.rpAgentLabel, '${data.agentEmployeeCode} (${data.agentShortName})', medium, semibold),
          _dashedRule(),
          _field(l10n.rpClientIdLabel, data.clientMemberNo, medium, bold),
          pw.SizedBox(height: 10),
          _field(l10n.rpClientNameLabel, data.clientFullName.toUpperCase(), medium, bold),
          _dashedRule(),
          _amountBlock(data, l10n, bold, medium, semibold),
          _dashedRule(),
          pw.Align(
            alignment: pw.Alignment.centerLeft,
            child: pw.Text(l10n.rpDenominationBreakdownLabel,
                style: pw.TextStyle(font: medium, fontSize: 8.5, letterSpacing: 1.4, color: _inkSoft)),
          ),
          pw.SizedBox(height: 8),
          _denominationTable(data, l10n, regular, medium, bold),
          _dashedRule(),
          _footer(data, l10n, regular, medium, semibold, bold),
        ],
      ),
    );
  }

  static pw.Widget _metaRow(String label, String value, pw.Font medium, pw.Font semibold) {
    return pw.Row(
      mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
      children: [
        pw.Text(label, style: pw.TextStyle(font: medium, fontSize: 10, color: _inkSoft)),
        pw.Expanded(
          child: pw.Text(value,
              textAlign: pw.TextAlign.right,
              style: pw.TextStyle(font: semibold, fontSize: 10, color: _ink)),
        ),
      ],
    );
  }

  static pw.Widget _field(String label, String value, pw.Font medium, pw.Font bold) {
    return pw.Column(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Text(label, style: pw.TextStyle(font: medium, fontSize: 8, letterSpacing: 1, color: _inkFaint)),
        pw.SizedBox(height: 2),
        pw.Text(value, style: pw.TextStyle(font: bold, fontSize: 12.5, color: _ink)),
      ],
    );
  }

  static pw.Widget _amountBlock(ReceiptData data, AppLocalizations l10n, pw.Font bold, pw.Font medium, pw.Font semibold) {
    return pw.Column(
      children: [
        pw.Text(l10n.rpAmountCollectedLabel,
            style: pw.TextStyle(font: medium, fontSize: 8.5, letterSpacing: 1.6, color: _inkSoft)),
        pw.SizedBox(height: 6),
        pw.RichText(
          text: pw.TextSpan(children: [
            pw.TextSpan(
                text: '${_grouped(data.amountXaf)} ',
                style: pw.TextStyle(font: bold, fontSize: 26, color: _navy)),
            pw.TextSpan(text: 'XAF', style: pw.TextStyle(font: bold, fontSize: 15, color: _navy)),
          ]),
        ),
        pw.SizedBox(height: 4),
        pw.Text('(${data.amountWords})',
            style: pw.TextStyle(font: medium, fontSize: 9.5, fontFallback: [medium], color: _inkSoft, fontStyle: pw.FontStyle.italic)),
        pw.SizedBox(height: 12),
        pw.Row(
          mainAxisAlignment: pw.MainAxisAlignment.center,
          children: [
            _pill(l10n.rpCashPill, _pillBg, _inkSoft, semibold),
            pw.SizedBox(width: 8),
            _pill(l10n.rpRecordedPill, _greenBg, _green, semibold),
          ],
        ),
      ],
    );
  }

  static pw.Widget _pill(String text, PdfColor bg, PdfColor fg, pw.Font font) {
    return pw.Container(
      height: 20,
      padding: const pw.EdgeInsets.symmetric(horizontal: 10),
      alignment: pw.Alignment.center,
      // circular(10) == half the 20pt height, giving a proper stadium/pill shape. Unlike a
      // browser, the pdf package doesn't clamp an oversized radius (e.g. CSS's "999px" trick for
      // "always fully rounded") to the box size — it uses it as a literal Bezier control offset,
      // which produced huge distorted spikes overshooting the whole page when tried here.
      decoration: pw.BoxDecoration(color: bg, borderRadius: pw.BorderRadius.circular(10)),
      child: pw.Text(text, style: pw.TextStyle(font: font, fontSize: 9, letterSpacing: 0.4, color: fg)),
    );
  }

  static pw.Widget _denominationTable(ReceiptData data, AppLocalizations l10n, pw.Font regular, pw.Font medium, pw.Font bold) {
    final total = data.denominationLines.fold<int>(0, (sum, l) => sum + l.lineTotalXaf);
    pw.Widget row(String d, String x, String s, {bool empty = false, bool isTotal = false}) {
      final color = empty ? _inkFaint : _ink;
      return pw.Padding(
        padding: pw.EdgeInsets.only(top: isTotal ? 7 : 3.5),
        child: pw.Row(
          children: [
            pw.SizedBox(
                width: _contentWidth * 0.38,
                child: pw.Text(d, style: pw.TextStyle(font: isTotal ? bold : regular, fontSize: isTotal ? 11 : 10.5, color: isTotal ? _ink : _inkSoft))),
            pw.SizedBox(
                width: _contentWidth * 0.24,
                child: pw.Text(x, textAlign: pw.TextAlign.center, style: pw.TextStyle(font: regular, fontSize: 10, color: _inkFaint))),
            pw.Expanded(
                child: pw.Text(s,
                    textAlign: pw.TextAlign.right,
                    style: pw.TextStyle(font: bold, fontSize: isTotal ? 11.5 : 10.5, color: color))),
          ],
        ),
      );
    }

    return pw.Column(children: [
      for (final line in data.denominationLines)
        row(_grouped(line.faceValueXaf), '× ${line.quantity}', line.quantity == 0 ? '—' : _grouped(line.lineTotalXaf), empty: line.quantity == 0),
      pw.Container(
        margin: const pw.EdgeInsets.only(top: 7),
        decoration: const pw.BoxDecoration(border: pw.Border(top: pw.BorderSide(color: _ink, width: 1.4))),
        child: row(l10n.commonTotal, '', _grouped(total), isTotal: true),
      ),
    ]);
  }

  static pw.Widget _footer(ReceiptData data, AppLocalizations l10n, pw.Font regular, pw.Font medium, pw.Font semibold, pw.Font bold) {
    return pw.Column(
      children: [
        pw.RichText(
          text: pw.TextSpan(children: [
            pw.TextSpan(text: '${l10n.rpUniqueRefLabel}  ', style: pw.TextStyle(font: medium, fontSize: 8.5, letterSpacing: 0.8, color: _inkFaint)),
          ]),
        ),
        pw.SizedBox(height: 3),
        pw.Text(data.uniqueRef,
            textAlign: pw.TextAlign.center,
            style: pw.TextStyle(font: regular, fontSize: 8.5, color: _inkSoft)),
        pw.SizedBox(height: 14),
        pw.Center(
          child: pw.BarcodeWidget(
            data: data.uniqueRef,
            barcode: pw.Barcode.qrCode(),
            drawText: false,
            width: 66,
            height: 66,
            color: _ink,
          ),
        ),
        pw.SizedBox(height: 10),
        pw.Text(data.signature, style: pw.TextStyle(font: bold, fontSize: 8, letterSpacing: 0.6, color: _navySoft)),
        pw.SizedBox(height: 14),
        pw.Text(
          l10n.rpElectronicReceiptProofNote,
          textAlign: pw.TextAlign.center,
          style: pw.TextStyle(font: regular, fontSize: 8, fontStyle: pw.FontStyle.italic, color: _inkFaint),
        ),
        pw.SizedBox(height: 14),
        pw.Text(l10n.rpThankYouTrust,
            style: pw.TextStyle(font: bold, fontSize: 10, letterSpacing: 1.8, color: _navy)),
      ],
    );
  }

  /// Solid rule using a real border (cheap) — kept separate from the dashed one, which needs a
  /// custom-drawn dash pattern the widget border API doesn't support.
  static pw.Widget _rule2({required PdfColor color, required double width, required double top, required double bottom}) {
    return pw.Container(
      margin: pw.EdgeInsets.only(top: top, bottom: bottom),
      width: _contentWidth,
      decoration: pw.BoxDecoration(border: pw.Border(top: pw.BorderSide(color: color, width: width))),
    );
  }

  static pw.Widget _dashedRule() {
    return pw.Container(
      margin: const pw.EdgeInsets.symmetric(vertical: 14),
      width: _contentWidth,
      height: 1.4,
      child: pw.CustomPaint(
        size: const PdfPoint(_contentWidth, 1.4),
        painter: (canvas, size) {
          canvas
            ..setColor(_rule)
            ..setLineWidth(1.4)
            ..setLineDashPattern([3, 2.5])
            ..moveTo(0, size.y / 2)
            ..lineTo(size.x, size.y / 2)
            ..strokePath();
        },
      ),
    );
  }

  static String _grouped(int n) {
    final digits = n.abs().toString();
    final buf = StringBuffer();
    for (int i = 0; i < digits.length; i++) {
      if (i > 0 && (digits.length - i) % 3 == 0) buf.write(' ');
      buf.write(digits[i]);
    }
    return (n < 0 ? '-' : '') + buf.toString();
  }
}

import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/receipt_pdf_builder.dart';
import 'package:microfi_mobile/features/collection/receipt_models.dart';
import 'package:microfi_mobile/l10n/app_localizations_en.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final l10n = AppLocalizationsEn();

  ReceiptData sample({
    String branchName = 'Yaoundé — Central Market',
    String clientFullName = 'Kamga Adele',
    String agentShortName = 'Fouda M.',
    int amountXaf = 25000,
    String amountWords = 'Twenty-five thousand CFA francs',
    List<ReceiptDenominationLine>? denominationLines,
    String uniqueRef = '7c1e-9f2a-44b0-8d3e-01af52c6',
  }) =>
      ReceiptData(
        branchName: branchName,
        dateFormatted: '03 Aug 2026 · 09:30 UTC',
        agentEmployeeCode: 'AF-042',
        agentShortName: agentShortName,
        clientMemberNo: 'MFI-2026-88391',
        clientFullName: clientFullName,
        amountXaf: amountXaf,
        amountWords: amountWords,
        denominationLines: denominationLines ??
            [
              ReceiptDenominationLine(faceValueXaf: 10000, quantity: 2, lineTotalXaf: 20000),
              ReceiptDenominationLine(faceValueXaf: 5000, quantity: 1, lineTotalXaf: 5000),
              ReceiptDenominationLine(faceValueXaf: 2000, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 1000, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 500, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 200, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 100, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 50, quantity: 0, lineTotalXaf: 0),
              ReceiptDenominationLine(faceValueXaf: 25, quantity: 0, lineTotalXaf: 0),
            ],
        uniqueRef: uniqueRef,
        signature: 'XAF · GEOTAG VALID · 03-08-26',
      );

  test('builds a valid single-page PDF for a typical receipt', () async {
    final bytes = await ReceiptPdfBuilder.build(sample(), l10n);

    expect(bytes.length, greaterThan(1000));
    expect(String.fromCharCodes(bytes.take(5)), '%PDF-');
  });

  test('handles long branch/client names and large amounts without throwing', () async {
    final bytes = await ReceiptPdfBuilder.build(sample(
      branchName: 'Douala Akwa-Nord Regional Commercial Hub Branch Office',
      clientFullName: 'Ngo Bikoro Marie-Christelle Adélaïde',
      agentShortName: 'Nguemetta Bikoula-Fouda M.',
      amountXaf: 1250000,
      amountWords: 'One million two hundred fifty thousand CFA francs',
      denominationLines: [
        ReceiptDenominationLine(faceValueXaf: 10000, quantity: 125, lineTotalXaf: 1250000),
        ReceiptDenominationLine(faceValueXaf: 5000, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 2000, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 1000, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 500, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 200, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 100, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 50, quantity: 0, lineTotalXaf: 0),
        ReceiptDenominationLine(faceValueXaf: 25, quantity: 0, lineTotalXaf: 0),
      ],
      uniqueRef: '7c1e-9f2a-44b0-8d3e-01af52c6-extra-long-local-uuid-suffix',
    ), l10n);

    expect(bytes.length, greaterThan(1000));
    expect(String.fromCharCodes(bytes.take(5)), '%PDF-');
  });
}

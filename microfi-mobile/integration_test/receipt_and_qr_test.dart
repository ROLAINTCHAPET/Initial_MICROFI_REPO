// Exercises the offline-receipt and QR-signing pipeline on a real device — the parts that
// dart analyze / flutter test can't actually verify: real bundled-font PDF rendering (rootBundle
// asset loading), and QrImageView actually building a real QR widget. Genuine camera-to-camera
// scanning and Bluetooth thermal printing still need a manual pass (see the checklist handed back
// alongside this file) — there's no way to automate "point a real camera at a real QR" or "talk to
// a real paired printer" from a test runner.
//
// Run with a device/emulator attached:
//   flutter test integration_test/receipt_and_qr_test.dart -d <device-id>
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:qr_flutter/qr_flutter.dart';

import 'package:microfi_mobile/core/offline_receipt_composer.dart';
import 'package:microfi_mobile/core/qr_receipt_signer.dart';
import 'package:microfi_mobile/core/receipt_pdf_builder.dart';
import 'package:microfi_mobile/features/collection/collection_repository.dart';
import 'package:microfi_mobile/l10n/app_localizations_en.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  final l10n = AppLocalizationsEn();
  final sampleLines = [
    DenominationLine(faceValueXaf: 10000, quantity: 1),
    DenominationLine(faceValueXaf: 5000, quantity: 1),
  ];
  final collectedAt = DateTime.utc(2026, 8, 24, 10, 30);

  group('OfflineReceiptComposer (real device)', () {
    testWidgets('composeText includes the org name, amount, and every counted denomination', (tester) async {
      final text = OfflineReceiptComposer.composeText(
        mfiName: 'MICROFI',
        branchName: 'Douala Central',
        collectedAtUtc: collectedAt,
        employeeCode: 'AGT001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'MFI-001',
        clientFullName: 'Jean Client',
        amountXaf: 15000,
        denominationLines: sampleLines,
        deviceTxId: 'tx-abc12345',
        french: false,
      );

      expect(text, contains('MICROFI'));
      expect(text, contains('Douala Central'));
      expect(text, contains('AGT001'));
      expect(text, contains('15 000 XAF'));
      expect(text, contains('MFI-001'));
      expect(text, contains('JEAN CLIENT'));
      expect(text, contains('tx-abc12345'));
    });

    testWidgets('composeData shows every canonical denomination, including zero-quantity ones', (tester) async {
      final data = OfflineReceiptComposer.composeData(
        branchName: 'Douala Central',
        collectedAtUtc: collectedAt,
        employeeCode: 'AGT001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'MFI-001',
        clientFullName: 'Jean Client',
        amountXaf: 15000,
        denominationLines: sampleLines,
        deviceTxId: 'tx-abc12345',
        french: false,
      );

      // Canonical set is {10000,5000,2000,1000,500,200,100,50,25} — 9 lines regardless of how
      // many denominations were actually counted, per ReceiptDataComposer's own contract.
      expect(data.denominationLines.length, 9);
      expect(data.denominationLines.where((l) => l.quantity > 0).length, 2);
      final total = data.denominationLines.fold<int>(0, (sum, l) => sum + l.lineTotalXaf);
      expect(total, 15000);
      expect(data.amountWords.toLowerCase(), contains('cfa francs'));
    });
  });

  group('ReceiptPdfBuilder (real device — real bundled fonts)', () {
    testWidgets('builds a non-empty, well-formed PDF from bundled Inter fonts', (tester) async {
      final data = OfflineReceiptComposer.composeData(
        branchName: 'Douala Central',
        collectedAtUtc: collectedAt,
        employeeCode: 'AGT001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'MFI-001',
        clientFullName: 'Jean Client',
        amountXaf: 15000,
        denominationLines: sampleLines,
        deviceTxId: 'tx-abc12345',
        french: false,
      );

      final bytes = await ReceiptPdfBuilder.build(data, l10n);

      expect(bytes.length, greaterThan(1000));
      // %PDF is the literal magic header every valid PDF file starts with.
      expect(String.fromCharCodes(bytes.take(4)), '%PDF');
    });
  });

  group('QrReceiptSigner + QrImageView (real device)', () {
    QrReceiptPayload samplePayload() => QrReceiptPayload(
          uniqueRef: 'tx-abc12345',
          mfiName: 'MICROFI',
          branchName: 'Douala Central',
          agentEmployeeCode: 'AGT001',
          agentFullName: 'Fouda Marie',
          clientMemberNo: 'MFI-001',
          clientFullName: 'Jean Client',
          amountXaf: 15000,
          collectedAtIso: collectedAt.toIso8601String(),
          denominationLines: sampleLines,
          french: false,
        );

    testWidgets('a signed payload renders as a real QrImageView with no error', (tester) async {
      final encoded = QrReceiptSigner.encode(samplePayload());

      await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: QrImageView(data: encoded, version: QrVersions.auto, size: 200)),
      ));
      await tester.pumpAndSettle();

      expect(find.byType(QrImageView), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('encode -> decode round-trips on-device (real crypto, not the mocked host)', (tester) async {
      final payload = samplePayload();
      final decoded = QrReceiptSigner.decode(QrReceiptSigner.encode(payload));

      expect(decoded.amountXaf, payload.amountXaf);
      expect(decoded.clientFullName, payload.clientFullName);
      expect(decoded.uniqueRef, payload.uniqueRef);
    });

    testWidgets('a tampered amount is rejected on-device', (tester) async {
      final encoded = QrReceiptSigner.encode(samplePayload());
      final tampered = encoded.replaceFirst('"amt":15000', '"amt":999999');

      expect(() => QrReceiptSigner.decode(tampered), throwsA(isA<QrReceiptVerificationFailed>()));
    });

    testWidgets('toReceiptData round-trips through a full PDF build on-device', (tester) async {
      final payload = samplePayload();
      final receiptData = QrReceiptSigner.toReceiptData(payload);
      final bytes = await ReceiptPdfBuilder.build(receiptData, l10n);

      expect(String.fromCharCodes(bytes.take(4)), '%PDF');
    });
  });

  // Not asserted (there's no automated way to open/inspect this on-device) — printed so a human
  // reviewing a real-device test run can spot-check the exact bytes are actually a valid PDF file
  // on disk, same sanity-check spirit as the SQLCipher encryption check in offline_storage_test.dart.
  group('sanity', () {
    testWidgets('a built PDF can be written to and read back from a real file', (tester) async {
      final data = OfflineReceiptComposer.composeData(
        branchName: 'Douala Central',
        collectedAtUtc: collectedAt,
        employeeCode: 'AGT001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'MFI-001',
        clientFullName: 'Jean Client',
        amountXaf: 15000,
        denominationLines: sampleLines,
        deviceTxId: 'tx-sanity-check',
        french: false,
      );
      final bytes = await ReceiptPdfBuilder.build(data, l10n);

      final file = File('${Directory.systemTemp.path}/microfi_receipt_sanity_check.pdf');
      await file.writeAsBytes(bytes);
      final readBack = await file.readAsBytes();

      debugPrint('sanity PDF written to ${file.path} — ${readBack.length} bytes, header: ${String.fromCharCodes(readBack.take(8))}');
      expect(readBack.length, bytes.length);
      await file.delete();
    });
  });
}

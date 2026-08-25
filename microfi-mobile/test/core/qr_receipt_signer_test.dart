import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/qr_receipt_signer.dart';
import 'package:microfi_mobile/features/collection/collection_repository.dart';

QrReceiptPayload _samplePayload() => QrReceiptPayload(
      uniqueRef: 'tx-12345678',
      mfiName: 'MICROFI',
      branchName: 'Douala Central',
      agentEmployeeCode: 'AGT001',
      agentFullName: 'Fouda Marie',
      clientMemberNo: 'MFI-001',
      clientFullName: 'Jean Client',
      amountXaf: 15000,
      collectedAtIso: '2026-08-24T10:00:00.000Z',
      denominationLines: [DenominationLine(faceValueXaf: 10000, quantity: 1), DenominationLine(faceValueXaf: 5000, quantity: 1)],
      french: false,
    );

void main() {
  group('QrReceiptSigner', () {
    test('encode then decode round-trips every field', () {
      final payload = _samplePayload();
      final decoded = QrReceiptSigner.decode(QrReceiptSigner.encode(payload));

      expect(decoded.uniqueRef, payload.uniqueRef);
      expect(decoded.mfiName, payload.mfiName);
      expect(decoded.branchName, payload.branchName);
      expect(decoded.agentEmployeeCode, payload.agentEmployeeCode);
      expect(decoded.agentFullName, payload.agentFullName);
      expect(decoded.clientMemberNo, payload.clientMemberNo);
      expect(decoded.clientFullName, payload.clientFullName);
      expect(decoded.amountXaf, payload.amountXaf);
      expect(decoded.collectedAtIso, payload.collectedAtIso);
      expect(decoded.denominationLines.length, payload.denominationLines.length);
    });

    test('rejects a payload whose amount was altered after signing', () {
      final encoded = QrReceiptSigner.encode(_samplePayload());
      final tampered = encoded.replaceFirst('"amt":15000', '"amt":150000');

      expect(() => QrReceiptSigner.decode(tampered), throwsA(isA<QrReceiptVerificationFailed>()));
    });

    test('rejects a payload whose client was swapped after signing', () {
      final encoded = QrReceiptSigner.encode(_samplePayload());
      final tampered = encoded.replaceFirst('"cfn":"Jean Client"', '"cfn":"Someone Else"');

      expect(() => QrReceiptSigner.decode(tampered), throwsA(isA<QrReceiptVerificationFailed>()));
    });

    test('rejects a QR code with no signature at all (a hand-crafted fake)', () {
      const fake = '{"tx":"fake","mfi":"MICROFI","amt":999999}';
      expect(() => QrReceiptSigner.decode(fake), throwsA(isA<QrReceiptVerificationFailed>()));
    });

    test('rejects content that isn\'t JSON at all', () {
      expect(() => QrReceiptSigner.decode('not a qr payload'), throwsA(isA<QrReceiptVerificationFailed>()));
    });

    test('toReceiptData produces the same amount and client identity as the payload', () {
      final payload = _samplePayload();
      final receiptData = QrReceiptSigner.toReceiptData(payload);

      expect(receiptData.amountXaf, payload.amountXaf);
      expect(receiptData.clientFullName, payload.clientFullName);
      expect(receiptData.clientMemberNo, payload.clientMemberNo);
      expect(receiptData.uniqueRef, payload.uniqueRef);
      expect(receiptData.denominationLines.length, payload.denominationLines.length);
    });
  });
}

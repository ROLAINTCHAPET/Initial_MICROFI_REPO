import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/collection_chain_codec.dart';

/// Fixed vectors shared with the Java counterpart
/// (microfi-core/src/test/java/com/microfi/transactions/service/CollectionChainCodecTest.java) —
/// both must produce byte-identical output. The expected values were independently computed with
/// Python's hashlib/hmac (a reference implementation, not either codebase's own code) — if either
/// implementation ever drifts from these vectors, both test suites fail, not just one silently
/// disagreeing with the other.
void main() {
  const installationId = 'INSTALL-1';
  const deviceTxId = 'DEV-TX-1';
  const clientId = '11111111-1111-1111-1111-111111111111';
  const amountXaf = 5000;
  const lat = 4.05;
  const lon = 9.7;
  const collectedAtEpochMilli = 1700000000000;
  const counter = 1;
  const secretBase64 = 'Zml4ZWQtdGVzdC1zZWNyZXQtMzItYnl0ZXMtcGFkIQ==';

  group('CollectionChainCodec', () {
    test('canonicalString is fixed-order pipe-delimited', () {
      final canonical = CollectionChainCodec.canonicalString(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );

      expect(canonical,
          'INSTALL-1|DEV-TX-1|11111111-1111-1111-1111-111111111111|5000|4.050000|9.700000|1700000000000|1|GENESIS');
    });

    test('computeHash matches the independently computed fixed vector', () {
      final hash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );

      expect(hash, '0o1DcqiNHAb9l1mhKRej7TCdi1gaO+I6HXg70bLpzgY=');
    });

    test('computeSignature matches the independently computed fixed vector', () {
      final hash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );
      final signature = CollectionChainCodec.computeSignature(installationSecretBase64: secretBase64, currentHash: hash);

      expect(signature, '5lnEAxv08teiwoQabvpDGaUG67VbZqS1O/aYzE6FybU=');
    });

    test('computeHash changes when any field changes', () {
      final baseHash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );
      final tamperedHash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf + 1,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );

      expect(tamperedHash, isNot(baseHash));
    });

    test('chain links the second record to the firsts hash', () {
      final firstHash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: deviceTxId,
        clientId: clientId,
        amountXaf: amountXaf,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: collectedAtEpochMilli,
        counter: counter,
        previousHash: CollectionChainCodec.genesisHash,
      );
      final secondHash = CollectionChainCodec.computeHash(
        installationId: installationId,
        deviceTxId: 'DEV-TX-2',
        clientId: clientId,
        amountXaf: 3000,
        lat: lat,
        lon: lon,
        collectedAtEpochMilli: 1700000060000,
        counter: 2,
        previousHash: firstHash,
      );

      expect(secondHash, 'yTnYQDTTrzjUXV8swzeOnsJGDTLF5ns3R+/Bm+cCV2g=');
    });
  });
}

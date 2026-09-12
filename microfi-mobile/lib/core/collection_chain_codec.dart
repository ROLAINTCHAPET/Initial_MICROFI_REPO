import 'dart:convert';

import 'package:crypto/crypto.dart';

/// Offline Field Collection Security Algorithm v1.1 §5/§12 hash-chain codec — the Dart mirror of
/// microfi-core's CollectionChainCodec.java. Must produce byte-identical output to it; see
/// test/core/collection_chain_codec_test.dart, which shares fixed vectors with
/// CollectionChainCodecTest.java on the server to catch any drift between the two.
///
/// An explicit, fixed-order delimited string rather than JSON — same reasoning as
/// QrReceiptSigner's _canonicalString. lat/lon are fixed to 6 decimal places and the timestamp is
/// the raw UTC epoch millisecond integer (not an ISO-8601 string) specifically to remove any
/// dependency on Dart's and Java's default number/date formatting agreeing byte-for-byte.
class CollectionChainCodec {
  /// The previousHash value for the first record in a chain (counter == 1) — mirrors the spec's GENESIS_HASH.
  static const genesisHash = 'GENESIS';

  static String canonicalString({
    required String installationId,
    required String deviceTxId,
    required String clientId,
    required int amountXaf,
    required double lat,
    required double lon,
    required int collectedAtEpochMilli,
    required int counter,
    required String previousHash,
  }) {
    return [
      installationId,
      deviceTxId,
      clientId,
      amountXaf.toString(),
      lat.toStringAsFixed(6),
      lon.toStringAsFixed(6),
      collectedAtEpochMilli.toString(),
      counter.toString(),
      previousHash,
    ].join('|');
  }

  /// Base64(SHA-256(canonicalString)).
  static String computeHash({
    required String installationId,
    required String deviceTxId,
    required String clientId,
    required int amountXaf,
    required double lat,
    required double lon,
    required int collectedAtEpochMilli,
    required int counter,
    required String previousHash,
  }) {
    final canonical = canonicalString(
      installationId: installationId,
      deviceTxId: deviceTxId,
      clientId: clientId,
      amountXaf: amountXaf,
      lat: lat,
      lon: lon,
      collectedAtEpochMilli: collectedAtEpochMilli,
      counter: counter,
      previousHash: previousHash,
    );
    return base64Encode(sha256.convert(utf8.encode(canonical)).bytes);
  }

  /// Base64(HMAC-SHA256(installationSecret, currentHash)).
  static String computeSignature({required String installationSecretBase64, required String currentHash}) {
    final secretBytes = base64Decode(installationSecretBase64);
    final signatureBytes = Hmac(sha256, secretBytes).convert(utf8.encode(currentHash)).bytes;
    return base64Encode(signatureBytes);
  }
}

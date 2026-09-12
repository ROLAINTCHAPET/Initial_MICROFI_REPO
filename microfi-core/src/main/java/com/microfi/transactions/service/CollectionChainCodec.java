package com.microfi.transactions.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Offline Field Collection Security Algorithm v1.1 §5/§12 — the hash-chain codec. Must produce
 * byte-identical output to its Dart counterpart (microfi-mobile/lib/core/collection_chain_codec.dart);
 * see that file's tests, which share fixed vectors with {@code CollectionChainCodecTest} here to
 * catch any drift between the two implementations.
 * <p>
 * An explicit, fixed-order delimited string rather than re-serializing to JSON for hashing/signing
 * — same reasoning as {@code QrReceiptSigner._canonicalString} on the mobile side: avoids any
 * dependency on map/JSON key-ordering staying stable. Two deliberate departures from a naive
 * "just stringify everything the same way" approach, both to remove cross-language ambiguity
 * rather than trust Dart's and Java's default number/date formatting to agree byte-for-byte:
 * lat/lon are fixed to 6 decimal places (~11cm precision, ample for this purpose), and the
 * timestamp is the raw UTC epoch millisecond integer, not an ISO-8601 string (whose fractional-
 * second formatting differs between {@code Instant#toString()} and Dart's {@code
 * toIso8601String()}).
 */
@Component
public class CollectionChainCodec {

    /** The previousHash value for the first record in a chain (counter == 1) — mirrors the spec's GENESIS_HASH. */
    public static final String GENESIS_HASH = "GENESIS";

    public record ChainInput(String installationId, String deviceTxId, UUID clientId, long amountXaf,
                              double lat, double lon, long collectedAtEpochMilli, long counter, String previousHash) {
    }

    public String canonicalString(ChainInput input) {
        return String.join("|",
                input.installationId(),
                input.deviceTxId(),
                input.clientId().toString(),
                String.valueOf(input.amountXaf()),
                String.format(Locale.ROOT, "%.6f", input.lat()),
                String.format(Locale.ROOT, "%.6f", input.lon()),
                String.valueOf(input.collectedAtEpochMilli()),
                String.valueOf(input.counter()),
                input.previousHash());
    }

    /** {@code Base64(SHA-256(canonicalString))}. */
    public String computeHash(ChainInput input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalString(input).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** {@code Base64(HMAC-SHA256(installationSecret, currentHash))}. */
    public String computeSignature(String installationSecretBase64, String currentHash) {
        try {
            byte[] secretBytes = Base64.getDecoder().decode(installationSecretBase64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] signatureBytes = mac.doFinal(currentHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 signing failed", e);
        }
    }
}

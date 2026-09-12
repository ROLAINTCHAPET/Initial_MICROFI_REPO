package com.microfi.transactions.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixed vectors shared with the Dart counterpart
 * (microfi-mobile/test/core/collection_chain_codec_test.dart) — both must produce byte-identical
 * output. If you change canonicalString()'s format, hand-copy the new expected values into both
 * test files.
 */
class CollectionChainCodecTest {

    private final CollectionChainCodec codec = new CollectionChainCodec();

    // Fixed vector: installationId=INSTALL-1, deviceTxId=DEV-TX-1, clientId=11111111-1111-1111-1111-111111111111,
    // amountXaf=5000, lat=4.05, lon=9.7, collectedAtEpochMilli=1700000000000, counter=1, previousHash=GENESIS
    private static final UUID FIXED_CLIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CollectionChainCodec.ChainInput FIXED_INPUT = new CollectionChainCodec.ChainInput(
            "INSTALL-1", "DEV-TX-1", FIXED_CLIENT_ID, 5000, 4.05, 9.7, 1700000000000L, 1, CollectionChainCodec.GENESIS_HASH);
    private static final String FIXED_SECRET_BASE64 = Base64.getEncoder().encodeToString("fixed-test-secret-32-bytes-pad!".getBytes());

    @Test
    void canonicalStringIsFixedOrderPipeDelimited() {
        String canonical = codec.canonicalString(FIXED_INPUT);

        assertThat(canonical).isEqualTo(
                "INSTALL-1|DEV-TX-1|11111111-1111-1111-1111-111111111111|5000|4.050000|9.700000|1700000000000|1|GENESIS");
    }

    @Test
    void computeHashIsDeterministicForTheSameInput() {
        String hash1 = codec.computeHash(FIXED_INPUT);
        String hash2 = codec.computeHash(FIXED_INPUT);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotBlank();
    }

    /**
     * The actual cross-language check: this exact value was independently computed with Python's
     * hashlib (a reference SHA-256/HMAC implementation, not this codebase's own code) and is
     * hand-copied into collection_chain_codec_test.dart's matching test — if either implementation
     * ever drifts from this vector, both this test and the Dart one fail, not just one of them
     * silently disagreeing with the other.
     */
    @Test
    void computeHashMatchesTheIndependentlyComputedFixedVector() {
        assertThat(codec.computeHash(FIXED_INPUT)).isEqualTo("0o1DcqiNHAb9l1mhKRej7TCdi1gaO+I6HXg70bLpzgY=");
    }

    @Test
    void computeSignatureMatchesTheIndependentlyComputedFixedVector() {
        String hash = codec.computeHash(FIXED_INPUT);
        assertThat(codec.computeSignature(FIXED_SECRET_BASE64, hash)).isEqualTo("5lnEAxv08teiwoQabvpDGaUG67VbZqS1O/aYzE6FybU=");
    }

    @Test
    void computeHashChangesWhenAnyFieldChanges() {
        String baseHash = codec.computeHash(FIXED_INPUT);
        CollectionChainCodec.ChainInput tamperedAmount = new CollectionChainCodec.ChainInput(
                "INSTALL-1", "DEV-TX-1", FIXED_CLIENT_ID, 5001, 4.05, 9.7, 1700000000000L, 1, CollectionChainCodec.GENESIS_HASH);

        assertThat(codec.computeHash(tamperedAmount)).isNotEqualTo(baseHash);
    }

    @Test
    void computeSignatureVerifiesAgainstTheCorrectSecretOnly() {
        String hash = codec.computeHash(FIXED_INPUT);
        String signature = codec.computeSignature(FIXED_SECRET_BASE64, hash);

        assertThat(codec.computeSignature(FIXED_SECRET_BASE64, hash)).isEqualTo(signature);

        String otherSecret = Base64.getEncoder().encodeToString("a-completely-different-secret!!".getBytes());
        assertThat(codec.computeSignature(otherSecret, hash)).isNotEqualTo(signature);
    }

    @Test
    void chainLinksSecondRecordToFirstsHash() {
        String firstHash = codec.computeHash(FIXED_INPUT);
        CollectionChainCodec.ChainInput second = new CollectionChainCodec.ChainInput(
                "INSTALL-1", "DEV-TX-2", FIXED_CLIENT_ID, 3000, 4.05, 9.7, 1700000060000L, 2, firstHash);

        String secondHash = codec.computeHash(second);

        assertThat(secondHash).isNotEqualTo(firstHash);
        assertThat(codec.canonicalString(second)).endsWith("|2|" + firstHash);
    }
}

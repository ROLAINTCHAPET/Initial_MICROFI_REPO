package com.microfi.mw.service;

import com.microfi.mw.domain.IdempotencyKey;
import com.microfi.mw.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Guards money-moving CBS operations (fee split, escrow credit, transaction post) against
 * duplicate execution when a caller retries a request under network uncertainty.
 * Keyed by the caller-supplied {@code Idempotency-Key} header.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public <T> T executeIdempotent(String idempotencyKey, String operation, Object request, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String requestHash = hash(request);
        var existing = idempotencyKeyRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, requestHash, responseType);
        }

        T result = action.get();
        try {
            idempotencyKeyRepository.save(IdempotencyKey.builder()
                    .key(idempotencyKey)
                    .operation(operation)
                    .requestHash(requestHash)
                    .responseBody(writeJson(result))
                    .build());
        } catch (DataIntegrityViolationException raceLost) {
            return idempotencyKeyRepository.findById(idempotencyKey)
                    .map(record -> replay(record, idempotencyKey, requestHash, responseType))
                    .orElseThrow(() -> raceLost);
        }
        return result;
    }

    private <T> T replay(IdempotencyKey record, String idempotencyKey, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key '" + idempotencyKey + "' was already used with a different request payload");
        }
        log.info("Idempotency-Key {} already processed; returning cached response", idempotencyKey);
        return readJson(record.getResponseBody(), responseType);
    }

    private String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to hash idempotent request", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize idempotent response", e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to deserialize cached idempotent response", e);
        }
    }
}

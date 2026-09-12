package com.microfi.events;

import com.microfi.shared.dto.CollectionRequest;
import com.microfi.transactions.domain.CollectionOrigin;

import java.util.UUID;

/**
 * The message CollectionRecordDispatcher sends and CollectionRecordListener consumes — the agent
 * id is resolved from the authenticated principal by the controller, same as the direct in-process
 * call this replaces. {@code origin} is which endpoint the request actually arrived on
 * (POST /collections vs. POST /collections/sync) — determined by the controller from the route,
 * never trusted from the request body, since CollectionService's online-first-collection gate
 * depends on it being unforgeable by the client.
 */
public record CollectionRecordRequest(
    UUID agentId,
    CollectionRequest request,
    CollectionOrigin origin
) {
}

package com.microfi.events;

import com.microfi.shared.dto.CollectionRequest;

import java.util.UUID;

/** The message CollectionRecordDispatcher sends and CollectionRecordListener consumes — the agent id is resolved from the authenticated principal by the controller, same as the direct in-process call this replaces. */
public record CollectionRecordRequest(
    UUID agentId,
    CollectionRequest request
) {
}

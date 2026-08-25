package com.microfi.events;

import com.microfi.shared.dto.CollectionResponse;

/**
 * What CollectionRecordListener sends back over RabbitMQ's direct reply-to. recordCollection
 * throws a ResponseStatusException for every expected rejection (wrong PIN, over ceiling,
 * outside geofence, inactive client, ...) — those aren't broker/processing failures, they're
 * real per-item outcomes the caller needs verbatim, so they're captured here rather than left to
 * propagate as an opaque listener exception (which would just time out the waiting caller with
 * no reason attached).
 */
public record CollectionRecordReply(
    boolean success,
    CollectionResponse response,
    Integer statusCode,
    String errorMessage
) {
    public static CollectionRecordReply success(CollectionResponse response) {
        return new CollectionRecordReply(true, response, null, null);
    }

    public static CollectionRecordReply failure(int statusCode, String errorMessage) {
        return new CollectionRecordReply(false, null, statusCode, errorMessage);
    }
}

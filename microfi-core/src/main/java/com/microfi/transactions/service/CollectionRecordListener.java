package com.microfi.transactions.service;

import com.microfi.config.RabbitMQConfig;
import com.microfi.events.CollectionRecordReply;
import com.microfi.events.CollectionRecordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The actual work behind {@link CollectionRecordRequest} — calls the exact same
 * {@link CollectionService#recordCollection} every direct in-process call used to invoke, so
 * every business rule (idempotency, PIN, geofence, escrow ceiling, denomination validation)
 * behaves identically. Only the invocation mechanism changed: this method runs inside
 * collectionRecordContainerFactory's bounded consumer pool (see RabbitMQConfig), so it's this
 * queue's concurrency limit — not Core's general thread pool — that caps how many collections
 * get processed at once during a burst.
 */
@Service
@RequiredArgsConstructor
public class CollectionRecordListener {

    private final CollectionService collectionService;

    @RabbitListener(queues = RabbitMQConfig.COLLECTION_RECORD_QUEUE, containerFactory = "collectionRecordContainerFactory")
    public CollectionRecordReply onRecordRequest(CollectionRecordRequest request) {
        try {
            return CollectionRecordReply.success(collectionService.recordCollection(request.agentId(), request.request()));
        } catch (ResponseStatusException e) {
            // A rejection is a normal, expected outcome (wrong PIN, over ceiling, ...) — captured
            // here and sent back as data rather than left to propagate, which would just leave
            // CollectionRecordDispatcher's waiting caller to time out with no reason attached.
            return CollectionRecordReply.failure(e.getStatusCode().value(), e.getReason());
        }
    }
}

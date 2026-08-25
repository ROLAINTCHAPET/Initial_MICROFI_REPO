package com.microfi.events;

import com.microfi.config.RabbitMQConfig;
import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * The HTTP-facing half of collection-record burst protection: CollectionController calls
 * {@link #dispatch} exactly where it used to call CollectionService.recordCollection directly.
 * Underneath, this sends a {@link CollectionRecordRequest} over RabbitMQ's request-reply pattern
 * (direct reply-to — {@code amq.rabbitmq.reply-to}, no manually-declared reply queue needed) and
 * waits for {@link CollectionRecordListener}'s answer, which
 * {@link RabbitMQConfig#collectionRecordContainerFactory} processes with bounded concurrency —
 * that bound, not Core's general request-handling capacity, is what a reconnection burst actually
 * queues against. A business-rule rejection comes back as data (see {@link CollectionRecordReply})
 * and is re-thrown here as the identical {@link ResponseStatusException} a direct call would have
 * thrown, so this is a drop-in replacement, not a new contract.
 */
@Service
@RequiredArgsConstructor
public class CollectionRecordDispatcher {

    private final RabbitTemplate rabbitTemplate;

    public Mono<CollectionResponse> dispatch(UUID agentId, CollectionRequest request) {
        return Mono.fromCallable(() -> send(agentId, request))
                .subscribeOn(Schedulers.boundedElastic())
                // Mono.fromCallable treats a null return as an empty completion, not a value —
                // RabbitTemplate returns null on reply-timeout, so that case is handled below via
                // switchIfEmpty, not as a null check inside flatMap (which would never even run).
                .flatMap(reply -> reply.success()
                        ? Mono.just(reply.response())
                        : Mono.<CollectionResponse>error(new ResponseStatusException(HttpStatus.valueOf(reply.statusCode()), reply.errorMessage())))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        // The queue itself is still healthy here — this specific request just
                        // wasn't processed in time (an overwhelmed consumer pool, or the
                        // broker/listener being unavailable) — a distinct status from "rejected by
                        // a business rule" so the caller knows retrying is the right move.
                        "Collection processing is temporarily backed up — please retry")));
    }

    private CollectionRecordReply send(UUID agentId, CollectionRequest request) {
        return rabbitTemplate.convertSendAndReceiveAsType(
                RabbitMQConfig.COLLECTION_EXCHANGE, RabbitMQConfig.COLLECTION_RECORD_KEY,
                new CollectionRecordRequest(agentId, request),
                new ParameterizedTypeReference<CollectionRecordReply>() {
                });
    }
}

package com.microfi.events;

import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class CollectionRecordDispatcherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private CollectionRecordDispatcher dispatcher;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dispatcher = new CollectionRecordDispatcher(rabbitTemplate);
    }

    @SuppressWarnings("unchecked")
    private void stubReply(CollectionRecordReply reply) {
        // RabbitTemplate overloads convertSendAndReceiveAsType with a (String, Object,
        // MessagePostProcessor, PTR) variant too — any()'s inferred type is otherwise ambiguous
        // between the two, hence the explicit <Object> witness to pin it to the 4-arg
        // (exchange, routingKey, message, responseType) overload this dispatcher actually calls.
        when(rabbitTemplate.convertSendAndReceiveAsType(anyString(), anyString(), org.mockito.ArgumentMatchers.<Object>any(), any(ParameterizedTypeReference.class)))
                .thenReturn(reply);
    }

    @Test
    void resolvesWithTheResponseOnSuccess() {
        CollectionResponse response = CollectionResponse.builder().id(UUID.randomUUID()).agentId(agentId).amountXaf(5000).build();
        stubReply(CollectionRecordReply.success(response));

        StepVerifier.create(dispatcher.dispatch(agentId, new CollectionRequest()))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void reThrowsTheExactRejectionFromTheListener() {
        stubReply(CollectionRecordReply.failure(409, "would exceed ceiling"));

        StepVerifier.create(dispatcher.dispatch(agentId, new CollectionRequest()))
                .expectErrorSatisfies(e -> {
                    var ex = (ResponseStatusException) e;
                    org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    org.assertj.core.api.Assertions.assertThat(ex.getReason()).isEqualTo("would exceed ceiling");
                })
                .verify();
    }

    @Test
    void returnsServiceUnavailableOnReplyTimeout() {
        stubReply(null);

        StepVerifier.create(dispatcher.dispatch(agentId, new CollectionRequest()))
                .expectErrorSatisfies(e -> {
                    var ex = (ResponseStatusException) e;
                    org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value()).isEqualTo(503);
                })
                .verify();
    }
}

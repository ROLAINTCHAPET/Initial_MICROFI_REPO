package com.microfi.transactions.service;

import com.microfi.events.CollectionRecordRequest;
import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class CollectionRecordListenerTest {

    @Mock
    private CollectionService collectionService;

    private CollectionRecordListener listener;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new CollectionRecordListener(collectionService);
    }

    @Test
    void wrapsASuccessfulRecordingAsASuccessReply() {
        CollectionRequest request = new CollectionRequest();
        CollectionResponse response = CollectionResponse.builder().id(UUID.randomUUID()).agentId(agentId).amountXaf(5000).build();
        when(collectionService.recordCollection(eq(agentId), any())).thenReturn(response);

        var reply = listener.onRecordRequest(new CollectionRecordRequest(agentId, request));

        assertThat(reply.success()).isTrue();
        assertThat(reply.response()).isEqualTo(response);
        assertThat(reply.statusCode()).isNull();
    }

    @Test
    void wrapsABusinessRuleRejectionAsAFailureReply_notAnException() {
        CollectionRequest request = new CollectionRequest();
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "would exceed ceiling"))
                .when(collectionService).recordCollection(eq(agentId), any());

        var reply = listener.onRecordRequest(new CollectionRecordRequest(agentId, request));

        assertThat(reply.success()).isFalse();
        assertThat(reply.statusCode()).isEqualTo(409);
        assertThat(reply.errorMessage()).isEqualTo("would exceed ceiling");
        assertThat(reply.response()).isNull();
    }
}

package com.microfi.savings.service;

import com.microfi.savings.domain.ActivationRequest;
import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.repository.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivationRequestExpiryJobTest {

    @Mock
    private ActivationRequestRepository activationRequestRepository;

    private ActivationRequestExpiryJob job;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new ActivationRequestExpiryJob(activationRequestRepository);
        ReflectionTestUtils.setField(job, "pendingTimeoutHours", 24L);
    }

    @Test
    void expiresStalePendingRequests() {
        ActivationRequest stale = ActivationRequest.builder().id(UUID.randomUUID()).clientId(UUID.randomUUID())
                .createdAt(Instant.now().minus(30, ChronoUnit.HOURS)).status(ActivationRequestStatus.PENDING).build();
        when(activationRequestRepository.findByStatusAndCreatedAtBefore(eq(ActivationRequestStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        job.expireStalePendingRequests();

        assertThat(stale.getStatus()).isEqualTo(ActivationRequestStatus.EXPIRED);
        assertThat(stale.getCancelledAt()).isNotNull();
        assertThat(stale.getCancelReason()).contains("Automatically expired");
        verify(activationRequestRepository).saveAll(List.of(stale));
    }

    @Test
    void doesNothingWhenNoStaleRequests() {
        when(activationRequestRepository.findByStatusAndCreatedAtBefore(eq(ActivationRequestStatus.PENDING), any()))
                .thenReturn(List.of());

        job.expireStalePendingRequests();

        verify(activationRequestRepository, never()).saveAll(any());
    }
}

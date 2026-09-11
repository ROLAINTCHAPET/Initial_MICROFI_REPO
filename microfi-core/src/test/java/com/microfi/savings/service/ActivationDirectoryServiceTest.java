package com.microfi.savings.service;

import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ActivationPaymentRepository;
import com.microfi.savings.repository.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ActivationDirectoryServiceTest {

    @Mock
    private ActivationPaymentRepository activationPaymentRepository;
    @Mock
    private ActivationRequestRepository activationRequestRepository;
    @Mock
    private AccessTokenRepository accessTokenRepository;

    private ActivationDirectoryService service;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ActivationDirectoryService(activationPaymentRepository, activationRequestRepository, accessTokenRepository);
    }

    @Test
    void isClientActivatedTrueForActiveUnexpiredToken() {
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(300, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        assertThat(service.isClientActivated(clientId)).isTrue();
    }

    @Test
    void isClientActivatedTrueWhenExpiresAtIsNull() {
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(null).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        assertThat(service.isClientActivated(clientId)).isTrue();
    }

    @Test
    void isClientActivatedFalseWhenTokenHasExpired() {
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now().minus(400, ChronoUnit.DAYS)).expiresAt(Instant.now().minus(35, ChronoUnit.DAYS))
                .status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        assertThat(service.isClientActivated(clientId)).isFalse();
    }

    @Test
    void isClientActivatedFalseWhenNoActiveTokenExists() {
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(service.isClientActivated(clientId)).isFalse();
    }

    @Test
    void requireActivatedClientPassesSilentlyWhenActivated() {
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(300, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        service.requireActivatedClient(clientId);
    }

    @Test
    void requireActivatedClientThrows409WhenNotActivated() {
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActivatedClient(clientId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }
}

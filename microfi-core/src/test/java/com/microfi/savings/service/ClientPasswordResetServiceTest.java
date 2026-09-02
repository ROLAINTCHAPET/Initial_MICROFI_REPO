package com.microfi.savings.service;

import com.microfi.notifications.gateway.SmsGateway;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.registration.service.TemporaryCredentialGenerator;
import com.microfi.savings.domain.ClientPasswordResetOtp;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientPasswordResetOtpRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientPasswordResetServiceTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private ClientPasswordResetOtpRepository clientPasswordResetOtpRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SmsGatewayFactory smsGatewayFactory;
    @Mock
    private SmsGateway smsGateway;
    @Mock
    private TemporaryCredentialGenerator temporaryCredentialGenerator;

    private ClientPasswordResetService service;

    private final UUID clientId = UUID.randomUUID();

    private ClientProfile client(String pinHash) {
        return ClientProfile.builder().id(clientId).login("jean.client").phone("+237600000000").pinHash(pinHash).build();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClientPasswordResetService(clientProfileRepository, clientPasswordResetOtpRepository,
                passwordEncoder, smsGatewayFactory, temporaryCredentialGenerator);
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10L);
        ReflectionTestUtils.setField(service, "otpMaxAttempts", 5);
        when(smsGatewayFactory.getActiveGateway()).thenReturn(smsGateway);
    }

    @Test
    void requestResetSendsOtpForKnownActivatedClient() {
        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client("hashed-pin")));
        when(temporaryCredentialGenerator.generatePin()).thenReturn("123456");
        when(passwordEncoder.encode("123456")).thenReturn("hashed-otp");
        when(smsGateway.send(eq("+237600000000"), anyString())).thenReturn(reactor.core.publisher.Mono.just(new SmsSendResult(true, "ref-1", null)));

        StepVerifier.create(service.requestReset("jean.client")).verifyComplete();

        ArgumentCaptor<ClientPasswordResetOtp> captor = ArgumentCaptor.forClass(ClientPasswordResetOtp.class);
        verify(clientPasswordResetOtpRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(clientId);
        assertThat(captor.getValue().getOtpHash()).isEqualTo("hashed-otp");
        verify(smsGateway).send(eq("+237600000000"), anyString());
    }

    @Test
    void requestResetNoOpForUnknownLogin() {
        when(clientProfileRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        StepVerifier.create(service.requestReset("ghost")).verifyComplete();

        verify(clientPasswordResetOtpRepository, never()).save(any());
        verify(smsGateway, never()).send(any(), any());
    }

    @Test
    void requestResetNoOpForClientNeverActivated() {
        // A client with no pinHash yet (never self-activated, see ClientProfile's own doc) has
        // nothing to reset — treated the same as an unknown login.
        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client(null)));

        StepVerifier.create(service.requestReset("jean.client")).verifyComplete();

        verify(clientPasswordResetOtpRepository, never()).save(any());
        verify(smsGateway, never()).send(any(), any());
    }

    @Test
    void requestResetFailsWhenSmsGatewayFails() {
        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client("hashed-pin")));
        when(temporaryCredentialGenerator.generatePin()).thenReturn("123456");
        when(passwordEncoder.encode("123456")).thenReturn("hashed-otp");
        when(smsGateway.send(any(), any())).thenReturn(reactor.core.publisher.Mono.just(new SmsSendResult(false, null, "carrier down")));

        StepVerifier.create(service.requestReset("jean.client"))
                .expectErrorMatches(e -> e instanceof ResponseStatusException)
                .verify();
    }

    @Test
    void confirmResetUpdatesPinOnValidCode() {
        ClientProfile client = client("old-hash");
        ClientPasswordResetOtp otp = ClientPasswordResetOtp.builder().id(UUID.randomUUID()).clientId(clientId)
                .otpHash("hashed-otp").expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)).attempts(0).createdAt(Instant.now()).build();

        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client));
        when(clientPasswordResetOtpRepository.findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(clientId)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(passwordEncoder.encode("5678")).thenReturn("new-hash");

        StepVerifier.create(service.confirmReset("jean.client", "123456", "5678")).verifyComplete();

        assertThat(otp.getConsumedAt()).isNotNull();
        verify(clientProfileRepository).save(argThatPinHashEquals("new-hash"));
    }

    @Test
    void confirmResetRejectsWrongCodeAndIncrementsAttempts() {
        ClientProfile client = client("old-hash");
        ClientPasswordResetOtp otp = ClientPasswordResetOtp.builder().id(UUID.randomUUID()).clientId(clientId)
                .otpHash("hashed-otp").expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)).attempts(0).createdAt(Instant.now()).build();

        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client));
        when(clientPasswordResetOtpRepository.findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(clientId)).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hashed-otp")).thenReturn(false);

        StepVerifier.create(service.confirmReset("jean.client", "000000", "5678"))
                .expectError(InvalidCredentialsException.class)
                .verify();

        assertThat(otp.getAttempts()).isEqualTo(1);
        verify(clientProfileRepository, never()).save(any());
    }

    @Test
    void confirmResetRejectsExpiredCode() {
        ClientProfile client = client("old-hash");
        ClientPasswordResetOtp otp = ClientPasswordResetOtp.builder().id(UUID.randomUUID()).clientId(clientId)
                .otpHash("hashed-otp").expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).attempts(0).createdAt(Instant.now()).build();

        when(clientProfileRepository.findByLogin("jean.client")).thenReturn(Optional.of(client));
        when(clientPasswordResetOtpRepository.findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(clientId)).thenReturn(Optional.of(otp));

        StepVerifier.create(service.confirmReset("jean.client", "123456", "5678"))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(clientProfileRepository, never()).save(any());
    }

    @Test
    void confirmResetRejectsUnknownLogin() {
        when(clientProfileRepository.findByLogin("ghost")).thenReturn(Optional.empty());

        StepVerifier.create(service.confirmReset("ghost", "123456", "5678"))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(clientPasswordResetOtpRepository, times(0)).findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(any());
    }

    private ClientProfile argThatPinHashEquals(String expectedHash) {
        return org.mockito.ArgumentMatchers.argThat(c -> c != null && expectedHash.equals(c.getPinHash()));
    }
}

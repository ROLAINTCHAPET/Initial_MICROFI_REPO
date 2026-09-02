package com.microfi.savings.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ActivationRequest;
import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ActivationPaymentRepository;
import com.microfi.savings.repository.ActivationRequestRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.dto.CancelActivationRequestRequest;
import com.microfi.shared.dto.ClientActivateRequest;
import com.microfi.shared.dto.ClientActivationPendingResponse;
import com.microfi.shared.dto.ClientActivationResponse;
import com.microfi.shared.dto.ClientPaymentConfirmationRequest;
import com.microfi.shared.dto.MiddlewareFeeSplit;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.transactions.service.CollectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class ClientActivationServiceTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private AccessTokenRepository accessTokenRepository;
    @Mock
    private ActivationPaymentRepository activationPaymentRepository;
    @Mock
    private ActivationRequestRepository activationRequestRepository;
    @Mock
    private CbsClientService cbsClientService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CollectionService collectionService;
    @Mock
    private MfiSettingsService mfiSettingsService;

    private ClientActivationService service;

    private final UUID clientId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private static final String LOGIN = "jean.client";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClientActivationService(clientProfileRepository, accessTokenRepository,
                activationPaymentRepository, activationRequestRepository, cbsClientService, passwordEncoder, collectionService,
                mfiSettingsService);
        ReflectionTestUtils.setField(service, "activationFeeXaf", 1000L);
        when(mfiSettingsService.getName()).thenReturn("MICROFI");
        when(activationPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accessTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(activationRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accessTokenRepository.findByClientIdAndStatus(clientId, AccessTokenStatus.ACTIVE)).thenReturn(List.of());
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.empty());
    }

    private ClientProfile clientWithoutCredentials() {
        return ClientProfile.builder().id(clientId).mfiMemberNo("M001").fullName("Jean Client")
                .cbsRef("CBS-1").build();
    }

    private ClientProfile activatedClient() {
        return ClientProfile.builder().id(clientId).mfiMemberNo("M001").fullName("Jean Client")
                .cbsRef("CBS-1").login(LOGIN).pinHash("hashed-pin").build();
    }

    private ClientActivateRequest activateRequest() {
        ClientActivateRequest request = new ClientActivateRequest();
        request.setMfiIdentifier("M001");
        request.setLogin(LOGIN);
        request.setPin("1234");
        return request;
    }

    private ClientPaymentConfirmationRequest paymentRequest(String pin) {
        ClientPaymentConfirmationRequest request = new ClientPaymentConfirmationRequest();
        request.setPin(pin);
        return request;
    }

    // --- selfActivate ---

    @Test
    void selfActivateSetsCredentialsWhenMfiIdentifierMatchesLocalRecord() {
        when(clientProfileRepository.findByMfiMemberNo("M001")).thenReturn(Optional.of(clientWithoutCredentials()));
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("hashed-pin");
        when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ClientActivationPendingResponse response = service.selfActivate(activateRequest());

        assertThat(response.getClientId()).isEqualTo(clientId);
        assertThat(response.getMfiMemberNo()).isEqualTo("M001");
        assertThat(response.getMfiName()).isEqualTo("MICROFI");
    }

    @Test
    void selfActivateRejectsUnknownMfiIdentifier() {
        when(clientProfileRepository.findByMfiMemberNo("M001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.selfActivate(activateRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void selfActivateRejectsLoginAlreadyTakenByAnotherClient() {
        when(clientProfileRepository.findByMfiMemberNo("M001")).thenReturn(Optional.of(clientWithoutCredentials()));
        ClientProfile someoneElse = ClientProfile.builder().id(UUID.randomUUID()).login(LOGIN).build();
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(someoneElse));

        assertThatThrownBy(() -> service.selfActivate(activateRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    // --- sponsorActivation (agent's half) ---

    @Test
    void sponsorActivationAloneReturnsAwaitingPayment() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));

        ClientActivationResponse response = service.sponsorActivation(LOGIN, agentId);

        assertThat(response.getStatus()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.getTokenExpiresAt()).isNull();
    }

    @Test
    void sponsorActivationRejectsUnknownLogin() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sponsorActivation(LOGIN, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void sponsorActivationRejectsWhenClientAlreadyActive() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));
        AccessToken unexpired = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(300, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findByClientIdAndStatus(clientId, AccessTokenStatus.ACTIVE)).thenReturn(List.of(unexpired));

        assertThatThrownBy(() -> service.sponsorActivation(LOGIN, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void sponsorActivationRejectsWhenEscrowCeilingExceeded() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Would exceed escrow ceiling (BR-03)"))
                .when(collectionService).enforceEscrowCeiling(agentId, 1000L);

        assertThatThrownBy(() -> service.sponsorActivation(LOGIN, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void sponsorActivationRejectsWhenAgentHasAnotherPendingActivation() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "pending client activation payment"))
                .when(collectionService).requireNoPendingActivation(agentId);

        assertThatThrownBy(() -> service.sponsorActivation(LOGIN, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void sponsorActivationRejectsDoubleSponsorship() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));
        ActivationRequest alreadySponsored = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .agentId(agentId).sponsoredAt(Instant.now()).build();
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.of(alreadySponsored));

        assertThatThrownBy(() -> service.sponsorActivation(LOGIN, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void sponsorActivationFinalizesWhenPaymentAlreadyConfirmed() {
        when(clientProfileRepository.findByLogin(LOGIN)).thenReturn(Optional.of(activatedClient()));
        ActivationRequest alreadyPaid = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .paidAt(Instant.now()).build();
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.of(alreadyPaid));
        when(cbsClientService.splitFee(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1000L), anyString()))
                .thenReturn(Mono.just(MiddlewareFeeSplit.builder().agentCommissionXaf(300).mfiShareXaf(700).reference("FEE-1").build()));

        ClientActivationResponse response = service.sponsorActivation(LOGIN, agentId);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getAgentCommissionXaf()).isEqualTo(300L);
        assertThat(response.getMfiShareXaf()).isEqualTo(700L);
        assertThat(response.getPaymentReference()).isEqualTo("FEE-1");
        assertThat(response.getTokenExpiresAt()).isAfter(Instant.now().plus(364, ChronoUnit.DAYS));
    }

    // --- confirmPayment (client's half) ---

    @Test
    void confirmPaymentAloneReturnsAwaitingSponsorship() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);

        ClientActivationResponse response = service.confirmPayment(clientId, paymentRequest("1234"));

        assertThat(response.getStatus()).isEqualTo("AWAITING_SPONSORSHIP");
        assertThat(response.getTokenExpiresAt()).isNull();
    }

    @Test
    void confirmPaymentRejectsWrongPin() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("0000", "hashed-pin")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmPayment(clientId, paymentRequest("0000")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void confirmPaymentRejectsWhenClientAlreadyActive() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        AccessToken unexpired = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(300, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findByClientIdAndStatus(clientId, AccessTokenStatus.ACTIVE)).thenReturn(List.of(unexpired));

        assertThatThrownBy(() -> service.confirmPayment(clientId, paymentRequest("1234")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void confirmPaymentRejectsDoubleConfirmation() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        ActivationRequest alreadyPaid = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .paidAt(Instant.now()).build();
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.of(alreadyPaid));

        assertThatThrownBy(() -> service.confirmPayment(clientId, paymentRequest("1234")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void confirmPaymentFinalizesWhenSponsorshipAlreadyRecorded() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        ActivationRequest alreadySponsored = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .agentId(agentId).sponsoredAt(Instant.now()).build();
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.of(alreadySponsored));
        when(cbsClientService.splitFee(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1000L), anyString()))
                .thenReturn(Mono.just(MiddlewareFeeSplit.builder().agentCommissionXaf(300).mfiShareXaf(700).reference("FEE-2").build()));

        ClientActivationResponse response = service.confirmPayment(clientId, paymentRequest("1234"));

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getPaymentReference()).isEqualTo("FEE-2");
    }

    @Test
    void finalizingRevokesAnyPreviouslyExpiredToken() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(activatedClient()));
        when(passwordEncoder.matches("1234", "hashed-pin")).thenReturn(true);
        ActivationRequest alreadySponsored = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .agentId(agentId).sponsoredAt(Instant.now()).build();
        when(activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)).thenReturn(Optional.of(alreadySponsored));
        AccessToken expired = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now().minus(400, ChronoUnit.DAYS)).expiresAt(Instant.now().minus(35, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findByClientIdAndStatus(clientId, AccessTokenStatus.ACTIVE)).thenReturn(List.of(expired));
        when(cbsClientService.splitFee(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1000L), anyString()))
                .thenReturn(Mono.just(MiddlewareFeeSplit.builder().agentCommissionXaf(300).mfiShareXaf(700).reference("FEE-3").build()));

        service.confirmPayment(clientId, paymentRequest("1234"));

        assertThat(expired.getStatus()).isEqualTo(AccessTokenStatus.REVOKED);
    }

    // --- cancelActivationRequest / listPendingForAgent (admin escape valve) ---

    @Test
    void cancelActivationRequestVoidsAPendingGate() {
        UUID requestId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ActivationRequest pending = ActivationRequest.builder().id(requestId).clientId(clientId)
                .agentId(agentId).sponsoredAt(Instant.now()).build();
        when(activationRequestRepository.findById(requestId)).thenReturn(Optional.of(pending));
        CancelActivationRequestRequest cancelRequest = new CancelActivationRequestRequest();
        cancelRequest.setReason("Client unreachable for 3 days");

        service.cancelActivationRequest(requestId, adminId, cancelRequest);

        assertThat(pending.getStatus()).isEqualTo(ActivationRequestStatus.CANCELLED);
        assertThat(pending.getCancelledBy()).isEqualTo(adminId);
        assertThat(pending.getCancelReason()).isEqualTo("Client unreachable for 3 days");
        assertThat(pending.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelActivationRequestRejectsUnknownId() {
        UUID requestId = UUID.randomUUID();
        when(activationRequestRepository.findById(requestId)).thenReturn(Optional.empty());
        CancelActivationRequestRequest cancelRequest = new CancelActivationRequestRequest();
        cancelRequest.setReason("x");

        assertThatThrownBy(() -> service.cancelActivationRequest(requestId, UUID.randomUUID(), cancelRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void cancelActivationRequestRejectsAlreadyCompleted() {
        UUID requestId = UUID.randomUUID();
        ActivationRequest completed = ActivationRequest.builder().id(requestId).clientId(clientId)
                .status(ActivationRequestStatus.COMPLETED).build();
        when(activationRequestRepository.findById(requestId)).thenReturn(Optional.of(completed));
        CancelActivationRequestRequest cancelRequest = new CancelActivationRequestRequest();
        cancelRequest.setReason("x");

        assertThatThrownBy(() -> service.cancelActivationRequest(requestId, UUID.randomUUID(), cancelRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void listPendingForAgentReturnsOpenGates() {
        ActivationRequest pending = ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId)
                .agentId(agentId).sponsoredAt(Instant.now()).build();
        when(activationRequestRepository.findByAgentIdAndStatus(agentId, ActivationRequestStatus.PENDING)).thenReturn(List.of(pending));

        var results = service.listPendingForAgent(agentId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getClientId()).isEqualTo(clientId);
    }
}

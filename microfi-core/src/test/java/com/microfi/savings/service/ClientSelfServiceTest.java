package com.microfi.savings.service;

import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import com.microfi.shared.dto.ClientRecentCollectionResponse;
import com.microfi.transactions.service.CollectionDirectoryService;
import com.microfi.transactions.service.CollectionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ClientSelfServiceTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private AccessTokenRepository accessTokenRepository;
    @Mock
    private CollectionDirectoryService collectionDirectoryService;
    @Mock
    private MfiSettingsService mfiSettingsService;

    private ClientSelfService service;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ClientSelfService(clientProfileRepository, accessTokenRepository, collectionDirectoryService, mfiSettingsService);
        when(mfiSettingsService.getName()).thenReturn("MICROFI");
    }

    private ClientProfile client() {
        return ClientProfile.builder().id(clientId).mfiMemberNo("M001").fullName("Jean Client")
                .phone("+237600000001").cbsRef("CBS-1").build();
    }

    @Test
    void profileReportsNoneWhenNeverActivated() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client()));
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ClientProfileSelfResponse response = service.getProfile(clientId);

        assertThat(response.getTokenStatus()).isEqualTo("NONE");
        assertThat(response.getTokenExpiresAt()).isNull();
    }

    @Test
    void profileReportsActiveWhenTokenNotExpired() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client()));
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(300, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        ClientProfileSelfResponse response = service.getProfile(clientId);

        assertThat(response.getTokenStatus()).isEqualTo("ACTIVE");
        assertThat(response.getTokenExpiresAt()).isEqualTo(token.getExpiresAt());
    }

    @Test
    void profileReportsExpiredButStillVisible() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client()));
        AccessToken token = AccessToken.builder().id(UUID.randomUUID()).clientId(clientId)
                .issuedAt(Instant.now().minus(400, ChronoUnit.DAYS)).expiresAt(Instant.now().minus(35, ChronoUnit.DAYS)).status(AccessTokenStatus.ACTIVE).build();
        when(accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE))
                .thenReturn(Optional.of(token));

        ClientProfileSelfResponse response = service.getProfile(clientId);

        assertThat(response.getTokenStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void profileThrowsWhenClientNotFound() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(clientId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getCbsRefReturnsClientsCbsReference() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client()));

        assertThat(service.getCbsRef(clientId)).isEqualTo("CBS-1");
    }

    @Test
    void getRecentCollectionsMapsFromCollectionDirectoryService() {
        UUID collectionId = UUID.randomUUID();
        Instant collectedAt = Instant.now();
        when(collectionDirectoryService.findRecentByClient(clientId)).thenReturn(
                List.of(new CollectionSummary(collectionId, UUID.randomUUID(), clientId, 5000L, "Akwa, Douala", collectedAt, 4.05, 9.7, "device-tx-1")));

        List<ClientRecentCollectionResponse> response = service.getRecentCollections(clientId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(collectionId);
        assertThat(response.get(0).getAmountXaf()).isEqualTo(5000L);
        assertThat(response.get(0).getLocationName()).isEqualTo("Akwa, Douala");
        assertThat(response.get(0).getCollectedAt()).isEqualTo(collectedAt);
    }
}

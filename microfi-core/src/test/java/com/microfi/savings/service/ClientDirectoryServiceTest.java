package com.microfi.savings.service;

import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the "portefeuille client" gate ({@link ClientDirectoryService#requireInPortfolio}) and
 * its assignment setter ({@link ClientDirectoryService#setAssignedAgent}) — the rest of the
 * class's methods are simple repository pass-throughs and don't need dedicated tests here.
 */
class ClientDirectoryServiceTest {

    @Mock
    private ClientProfileRepository clientProfileRepository;

    private ClientDirectoryService clientDirectoryService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clientDirectoryService = new ClientDirectoryService(clientProfileRepository);
    }

    // The "open to any agent in the branch" default confirmed with the user: an unassigned
    // client is never blocked by this check, regardless of which agent is calling — the
    // restriction only ever narrows an *existing* assignment, so bulk-imported/CBS clients
    // that were never sponsored by an agent stay collectible the moment a branch opts in.
    @Test
    void requireInPortfolioAllowsWhenClientIsUnassigned() {
        ClientProfile client = ClientProfile.builder().id(clientId).assignedAgentId(null).build();
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client));

        clientDirectoryService.requireInPortfolio(agentId, clientId);
    }

    @Test
    void requireInPortfolioAllowsWhenClientIsAssignedToThisAgent() {
        ClientProfile client = ClientProfile.builder().id(clientId).assignedAgentId(agentId).build();
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client));

        clientDirectoryService.requireInPortfolio(agentId, clientId);
    }

    @Test
    void requireInPortfolioRejectsWhenClientBelongsToAnotherAgent() {
        UUID otherAgentId = UUID.randomUUID();
        ClientProfile client = ClientProfile.builder().id(clientId).assignedAgentId(otherAgentId).build();
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientDirectoryService.requireInPortfolio(agentId, clientId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void requireInPortfolioUnknownClientThrows404() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientDirectoryService.requireInPortfolio(agentId, clientId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void setAssignedAgentSetsTheOwningAgent() {
        ClientProfile client = ClientProfile.builder().id(clientId).assignedAgentId(null).build();
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        clientDirectoryService.setAssignedAgent(clientId, agentId);

        assertThat(client.getAssignedAgentId()).isEqualTo(agentId);
        verify(clientProfileRepository).save(client);
    }

    // A null agentId is a deliberate "unassign" signal (see AssignClientAgentRequest), not an
    // invalid input — a manager clearing a client back to "open to any agent" must go through
    // this same method, not a separate one.
    @Test
    void setAssignedAgentClearsAssignmentWhenGivenNull() {
        ClientProfile client = ClientProfile.builder().id(clientId).assignedAgentId(agentId).build();
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        clientDirectoryService.setAssignedAgent(clientId, null);

        assertThat(client.getAssignedAgentId()).isNull();
    }

    @Test
    void setAssignedAgentUnknownClientThrows404() {
        when(clientProfileRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientDirectoryService.setAssignedAgent(clientId, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}

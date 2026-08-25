package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.shared.dto.ChangeAgentPinRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSelfServiceTest {

    @Mock
    private AgentRepository agentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AgentSelfService agentSelfService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentSelfService = new AgentSelfService(agentRepository, passwordEncoder);
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Agent buildAgent() {
        return Agent.builder().id(UUID.randomUUID()).branchId(UUID.randomUUID()).status(AgentStatus.ACTIVE)
                .pinHash("old-hashed-pin").pinMustChange(true).failedTransactionPinAttempts(2).build();
    }

    private ChangeAgentPinRequest request(String currentPin, String newPin) {
        ChangeAgentPinRequest request = new ChangeAgentPinRequest();
        request.setCurrentPin(currentPin);
        request.setNewPin(newPin);
        return request;
    }

    @Test
    void changePinRejectsWrongCurrentPin() {
        Agent agent = buildAgent();
        when(passwordEncoder.matches(eq("0000"), eq("old-hashed-pin"))).thenReturn(false);

        assertThatThrownBy(() -> agentSelfService.changePin(agent, request("0000", "7392")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        verify(agentRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1111", "1234", "9876", "0000"})
    void changePinRejectsWeakNewPin(String weakPin) {
        Agent agent = buildAgent();
        when(passwordEncoder.matches(eq("0000"), eq("old-hashed-pin"))).thenReturn(true);

        assertThatThrownBy(() -> agentSelfService.changePin(agent, request("0000", weakPin)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(agentRepository, never()).save(any());
    }

    @Test
    void changePinAcceptsNonSequentialNumericPin() {
        Agent agent = buildAgent();
        when(passwordEncoder.matches(eq("0000"), eq("old-hashed-pin"))).thenReturn(true);
        when(passwordEncoder.encode("7392")).thenReturn("new-hashed-pin");

        Agent saved = agentSelfService.changePin(agent, request("0000", "7392"));

        assertThat(saved.getPinHash()).isEqualTo("new-hashed-pin");
        assertThat(saved.getPinMustChange()).isFalse();
        assertThat(saved.getFailedTransactionPinAttempts()).isEqualTo(0);
        assertThat(saved.getTransactionPinLockedUntil()).isNull();
    }

    @Test
    void changePinAcceptsNonNumericPin() {
        // isWeakPin only judges purely-numeric PINs — anything else passes through untouched.
        Agent agent = buildAgent();
        when(passwordEncoder.matches(eq("0000"), eq("old-hashed-pin"))).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("new-hashed-pin");

        Agent saved = agentSelfService.changePin(agent, request("0000", "abcd"));

        assertThat(saved.getPinHash()).isEqualTo("new-hashed-pin");
    }
}

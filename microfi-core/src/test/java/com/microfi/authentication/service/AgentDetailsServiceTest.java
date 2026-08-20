package com.microfi.authentication.service;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class AgentDetailsServiceTest {

    private AgentDetailsService agentDetailsService;

    @Mock
    private AgentRepository agentRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentDetailsService = new AgentDetailsService(agentRepository);
        ReflectionTestUtils.setField(agentDetailsService, "maxFailedPinAttempts", 3);
        ReflectionTestUtils.setField(agentDetailsService, "lockoutMinutes", 15L);
    }

    @Test
    void testFindByUsernameFound() {
        Agent agent = Agent.builder().employeeCode("AGT123").username("agt.jane").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findByUsername("agt.jane")).thenReturn(Optional.of(agent));

        Mono<UserDetails> result = agentDetailsService.findByUsername("agt.jane");
        StepVerifier.create(result)
                .assertNext(userDetails -> {
                    assertThat(userDetails.getUsername()).isEqualTo("agt.jane");
                    assertThat(userDetails).isInstanceOf(AgentDetails.class);
                })
                .verifyComplete();
    }

    @Test
    void testFindByUsernameNotFound() {
        when(agentRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        Mono<UserDetails> result = agentDetailsService.findByUsername("unknown");
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable.getMessage().contains("Agent not found with username: unknown"))
                .verify();
    }

    @Test
    void registerFailedLoginAttemptIncrementsWithoutLockingBelowThreshold() {
        Agent agent = Agent.builder().employeeCode("AGT123").username("agt.jane").status(AgentStatus.ACTIVE).failedPinAttempts(1).build();
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDetailsService.registerFailedLoginAttempt(agent);

        assertThat(agent.getFailedPinAttempts()).isEqualTo(2);
        assertThat(agent.getLockedUntil()).isNull();
    }

    @Test
    void registerFailedLoginAttemptLocksOnceThresholdReached() {
        Agent agent = Agent.builder().employeeCode("AGT123").username("agt.jane").status(AgentStatus.ACTIVE).failedPinAttempts(2).build();
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDetailsService.registerFailedLoginAttempt(agent);

        assertThat(agent.getFailedPinAttempts()).isEqualTo(3);
        assertThat(agent.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void resetFailedLoginAttemptsClearsStateAndPersistsOnlyWhenNeeded() {
        Agent lockedAgent = Agent.builder().employeeCode("AGT123").username("agt.jane").status(AgentStatus.ACTIVE)
                .failedPinAttempts(3).lockedUntil(Instant.now().plusSeconds(600)).build();
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDetailsService.resetFailedLoginAttempts(lockedAgent);

        assertThat(lockedAgent.getFailedPinAttempts()).isZero();
        assertThat(lockedAgent.getLockedUntil()).isNull();
        verify(agentRepository, times(1)).save(lockedAgent);

        Agent freshAgent = Agent.builder().employeeCode("AGT456").username("agt.smith").status(AgentStatus.ACTIVE).build();
        agentDetailsService.resetFailedLoginAttempts(freshAgent);
        verify(agentRepository, times(0)).save(freshAgent);
    }
}

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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

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
    }

    @Test
    void testFindByUsernameFound() {
        Agent agent = Agent.builder().employeeCode("AGT123").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findByEmployeeCode("AGT123")).thenReturn(Optional.of(agent));

        Mono<UserDetails> result = agentDetailsService.findByUsername("AGT123");
        StepVerifier.create(result)
                .assertNext(userDetails -> {
                    assertThat(userDetails.getUsername()).isEqualTo("AGT123");
                    assertThat(userDetails).isInstanceOf(AgentDetails.class);
                })
                .verifyComplete();
    }

    @Test
    void testFindByUsernameNotFound() {
        when(agentRepository.findByEmployeeCode("UNKNOWN")).thenReturn(Optional.empty());

        Mono<UserDetails> result = agentDetailsService.findByUsername("UNKNOWN");
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> throwable.getMessage().contains("Agent not found with code: UNKNOWN"))
                .verify();
    }
}

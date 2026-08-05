package com.microfi.authentication;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.service.ClientDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a bug found while smoke-testing the Escrow module live: a downstream
 * business error (e.g. 404 from an authenticated endpoint) was being swallowed by the filter's
 * error handler and reported as 401, because {@code chain.filter(exchange)} was nested inside the
 * same {@code onErrorResume} meant only to catch agent-lookup failures. Not caught by
 * {@code @WithMockUser}-based controller tests since those never send a real Authorization header
 * through this filter.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private AgentDetailsService agentDetailsService;
    private AdminUserDetailsService adminUserDetailsService;
    private ClientDetailsService clientDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        agentDetailsService = mock(AgentDetailsService.class);
        adminUserDetailsService = mock(AdminUserDetailsService.class);
        clientDetailsService = mock(ClientDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, agentDetailsService, adminUserDetailsService, clientDetailsService);
    }

    @Test
    void downstreamBusinessErrorsAreNotMaskedAsUnauthorized() {
        Agent agent = Agent.builder().employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(jwtService.extractUsername("good-token")).thenReturn("AGT001");
        when(agentDetailsService.findByUsername("AGT001")).thenReturn(Mono.just(details));
        when(jwtService.isTokenValid("good-token", details)).thenReturn(true);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agents/x/escrow")
                        .header("Authorization", "Bearer good-token"));

        WebFilterChain chain = ex -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "no escrow account"));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    void unknownAgentIsRejectedAsUnauthorized() {
        when(jwtService.extractUsername("good-token")).thenReturn("GHOST");
        when(agentDetailsService.findByUsername("GHOST"))
                .thenReturn(Mono.error(new UsernameNotFoundException("Agent not found with code: GHOST")));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/agents/x/escrow")
                        .header("Authorization", "Bearer good-token"));

        WebFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

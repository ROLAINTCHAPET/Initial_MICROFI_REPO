package com.microfi.authentication.service;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private AgentDetails agentDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 1000 * 60 * 60);

        Agent agent = Agent.builder()
                .id(UUID.randomUUID())
                .employeeCode("AGT001")
                .username("AGT001")
                .status(AgentStatus.ACTIVE)
                .build();
        agentDetails = new AgentDetails(agent);
    }

    @Test
    void testGenerateTokenAndExtractUsername() {
        String token = jwtService.generateToken(agentDetails);
        assertThat(token).isNotBlank();

        String extractedUsername = jwtService.extractUsername(token);
        assertThat(extractedUsername).isEqualTo("AGT001");
    }

    @Test
    void testIsTokenValid() {
        String token = jwtService.generateToken(agentDetails);
        boolean isValid = jwtService.isTokenValid(token, agentDetails);
        assertThat(isValid).isTrue();
    }
}

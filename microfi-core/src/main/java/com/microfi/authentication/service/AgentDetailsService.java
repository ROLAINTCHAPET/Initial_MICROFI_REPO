package com.microfi.authentication.service;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deliberately does not implement {@code ReactiveUserDetailsService} — this app has a fully
 * custom {@link com.microfi.authentication.JwtAuthenticationFilter} that calls
 * {@link #findByUsername} directly, and with two principal-lookup services (this one and
 * {@link AdminUserDetailsService}) implementing that marker interface, Spring Security's own
 * auto-configuration tries to wire a single default {@code ReactiveUserDetailsService} and fails
 * with {@code NoUniqueBeanDefinitionException} — a real app-startup break, not just a test issue.
 */
@Service
@RequiredArgsConstructor
public class AgentDetailsService {

    private final AgentRepository agentRepository;

    @Value("${agent.auth.max-failed-pin-attempts:3}")
    private int maxFailedPinAttempts;

    @Value("${agent.auth.lockout-minutes:15}")
    private long lockoutMinutes;

    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() -> agentRepository.findByUsername(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalAgent -> optionalAgent
                        .map(agent -> Mono.just((UserDetails) new AgentDetails(agent)))
                        .orElseGet(() -> Mono.error(new UsernameNotFoundException("Agent not found with username: " + username))));
    }

    /** UC-01 §4.1: increments the failed-login-attempt counter and locks the account once it hits the configured threshold. */
    public void registerFailedLoginAttempt(Agent agent) {
        int attempts = (agent.getFailedPinAttempts() == null ? 0 : agent.getFailedPinAttempts()) + 1;
        agent.setFailedPinAttempts(attempts);
        if (attempts >= maxFailedPinAttempts) {
            agent.setLockedUntil(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
        }
        agentRepository.save(agent);
    }

    /** Clears the lockout state on a successful login — a fresh streak starts next time. */
    public void resetFailedLoginAttempts(Agent agent) {
        boolean hadState = (agent.getFailedPinAttempts() != null && agent.getFailedPinAttempts() > 0) || agent.getLockedUntil() != null;
        if (hadState) {
            agent.setFailedPinAttempts(0);
            agent.setLockedUntil(null);
            agentRepository.save(agent);
        }
    }

    /** Persists the device id an agent's first successful login just bound to their account (Branch#requireImei, AuthenticationController#login). */
    public void bindDevice(Agent agent) {
        agentRepository.save(agent);
    }
}

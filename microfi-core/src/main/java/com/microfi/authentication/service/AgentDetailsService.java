package com.microfi.authentication.service;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() -> agentRepository.findByEmployeeCode(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalAgent -> optionalAgent
                        .map(agent -> Mono.just((UserDetails) new AgentDetails(agent)))
                        .orElseGet(() -> Mono.error(new UsernameNotFoundException("Agent not found with code: " + username))));
    }
}

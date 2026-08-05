package com.microfi.savings.service;

import com.microfi.savings.ClientDetails;
import com.microfi.savings.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Plain blocking lookup — {@code savings} is a JPA/blocking-engine module (architecture.txt),
 * unlike {@code authentication}'s {@code AgentDetailsService}/{@code AdminUserDetailsService},
 * which return {@code Mono<UserDetails>} because {@code authentication} is a WebFlux-engine
 * module. Reactive callers (e.g. {@code JwtAuthenticationFilter}) wrap this in
 * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())} at their own boundary,
 * the same way {@code transactions.OfjService} calls {@code .block()} on the reactive
 * {@code cbsclient} module going the other direction.
 */
@Service
@RequiredArgsConstructor
public class ClientDetailsService {

    private final ClientProfileRepository clientProfileRepository;

    public UserDetails findByUsername(String login) {
        return clientProfileRepository.findByLogin(login)
                .map(client -> (UserDetails) new ClientDetails(client))
                .orElseThrow(() -> new UsernameNotFoundException("Client not found: " + login));
    }
}

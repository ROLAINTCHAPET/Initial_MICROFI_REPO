package com.microfi.authentication.service;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** See {@link AgentDetailsService}'s javadoc for why this deliberately doesn't implement {@code ReactiveUserDetailsService}. */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public Mono<UserDetails> findByUsername(String login) {
        return Mono.fromCallable(() -> adminUserRepository.findByLogin(login))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalAdmin -> optionalAdmin
                        .map(admin -> Mono.just((UserDetails) new AdminUserDetails(admin)))
                        .orElseGet(() -> Mono.error(new UsernameNotFoundException("Admin user not found: " + login))));
    }
}

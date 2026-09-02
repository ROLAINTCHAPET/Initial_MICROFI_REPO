package com.microfi.authentication;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Only login is unauthenticated. Everything under /api/v1/admin/** (agents,
                        // branches, clients, back-office users) now requires a real ADMIN/BRANCH_MANAGER/
                        // BRANCH_CASHIER token, enforced per-endpoint by AdminAccess — previously these
                        // were permitAll, i.e. wide open to anyone.
                        .pathMatchers("/api/v1/auth/**").permitAll()
                        .pathMatchers("/favicon.ico", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/swagger-ui/**").permitAll()
                        // Scraped by Prometheus over the internal Docker network only (see
                        // docker-compose.yml) — never routed through Kong, so this doesn't widen
                        // what's reachable from outside the host.
                        .pathMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

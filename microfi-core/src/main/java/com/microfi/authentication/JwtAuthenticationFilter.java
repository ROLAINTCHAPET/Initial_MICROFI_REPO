package com.microfi.authentication;

import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.service.ClientDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;
    private final AgentDetailsService agentDetailsService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final ClientDetailsService clientDetailsService;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        String username;
        String principalType;
        try {
            username = jwtService.extractUsername(token);
            principalType = jwtService.extractPrincipalType(token);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Mono<UserDetails> principalLookup;
        if (JwtService.PRINCIPAL_TYPE_ADMIN_USER.equals(principalType)) {
            principalLookup = adminUserDetailsService.findByUsername(username);
        } else if (JwtService.PRINCIPAL_TYPE_CLIENT.equals(principalType)) {
            // savings is a JPA/blocking-engine module, so ClientDetailsService.findByUsername is a
            // plain blocking call — wrap it here rather than making the service reactive.
            principalLookup = Mono.fromCallable(() -> clientDetailsService.findByUsername(username))
                    .subscribeOn(Schedulers.boundedElastic());
        } else {
            principalLookup = agentDetailsService.findByUsername(username);
        }

        return principalLookup
                .flatMap(userDetails -> {
                    if (jwtService.isTokenValid(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authToken));
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })
                // Only errors from resolving the agent's identity are auth failures. chain.filter(exchange)
                // is nested inside the same flatMap, so without narrowing to this type, any downstream
                // business error (404, 400, ...) would be swallowed and misreported as 401.
                .onErrorResume(UsernameNotFoundException.class, e -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}

package com.microfi.notifications.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Common scaffolding for {@link SmsGateway} implementations — mirrors
 * {@code AbstractCoreBankingAdapter} in the middleware, so a new carrier only has to implement
 * {@link #send}, not re-derive vendor-key wiring or token handling. Both Orange's and MTN's SMS
 * APIs authenticate the same way (standard OAuth2 client-credentials, {@code Basic} auth at the
 * token endpoint), so that flow — including caching the token until shortly before it expires,
 * rather than fetching one per SMS — lives here once instead of being duplicated per carrier.
 */
public abstract class AbstractSmsGateway implements SmsGateway {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final WebClient webClient;

    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    protected AbstractSmsGateway(WebClient webClient, String tokenUri, String clientId, String clientSecret) {
        this.webClient = webClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public final String vendor() {
        return vendorKey();
    }

    /** Unique vendor key this gateway implements, matched against the {@code sms.vendor} property. */
    protected abstract String vendorKey();

    /** Cached OAuth2 access token; refreshed automatically once it's within 30s of expiring. */
    protected synchronized Mono<String> accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return Mono.just(cachedToken);
        }
        String basicAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        return webClient.post()
                .uri(tokenUri)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=client_credentials")
                .retrieve()
                .bodyToMono(OAuthTokenResponse.class)
                .doOnNext(token -> {
                    this.cachedToken = token.accessToken();
                    this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(token.expiresIn() - 30, 0));
                })
                .doOnError(e -> log.error("{} OAuth2 token request failed: {}", vendorKey(), e.getMessage()))
                .map(OAuthTokenResponse::accessToken);
    }

    private record OAuthTokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }
}

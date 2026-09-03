package com.microfi.transactions.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Reverse geocoding for collection geotags — resolves a captured lat/lon into a human-readable
 * place name via OpenStreetMap's free Nominatim service, so collection records and the Back-Office
 * route map show where cash was actually collected rather than raw coordinates. Best-effort only:
 * a lookup failure or timeout never blocks the collection itself, it just leaves
 * {@code Collection#locationName} null (see CollectionService#recordCollection).
 */
@Service
@Slf4j
public class GeocodingService {

    private final WebClient webClient;
    private final int maxAttempts;
    private final Duration initialBackoff;

    /**
     * {@code maxAttempts}/{@code initialBackoffMs} are configurable (not just constants) so tests
     * can drive them down to near-zero delay instead of eating the real backoff wall-clock time.
     * Runs on {@link CollectionGeocodeListener}'s own (unbounded, default) container factory — a
     * distinct listener/queue from {@code collectionRecordContainerFactory}'s deliberately bounded
     * pool that gates collection recording itself (see that factory's javadoc) — so retrying here
     * costs nothing on the actual collect-cash critical path. Nominatim's free endpoint
     * occasionally answers just past the per-attempt timeout under normal load (observed ~3-5s
     * round trips); a single missed attempt used to permanently leave locationName null with
     * nothing else ever trying again, even though a retry moments later routinely succeeds.
     */
    public GeocodingService(WebClient.Builder webClientBuilder,
            @Value("${geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${geocoding.retry.max-attempts:3}") int maxAttempts,
            @Value("${geocoding.retry.initial-backoff-ms:1000}") long initialBackoffMs) {
        // Nominatim's usage policy requires an identifying User-Agent on every request.
        this.webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "MICROFI-Core/1.0 (field cash-collection app)")
                .build();
        this.maxAttempts = maxAttempts;
        this.initialBackoff = Duration.ofMillis(initialBackoffMs);
    }

    public String reverseGeocode(double lat, double lon) {
        try {
            NominatimResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("format", "jsonv2")
                            .build())
                    .retrieve()
                    .bodyToMono(NominatimResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .retryWhen(Retry.backoff(maxAttempts - 1, initialBackoff)
                            .maxBackoff(Duration.ofSeconds(4))
                            .doBeforeRetry(signal -> log.warn("Retrying reverse geocode for ({}, {}) after attempt {}: {}",
                                    lat, lon, signal.totalRetries() + 1, signal.failure().getMessage())))
                    .block();
            return response != null ? response.displayName() : null;
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}) after {} attempts: {}", lat, lon, maxAttempts, e.getMessage());
            return null;
        }
    }

    private record NominatimResponse(@JsonProperty("display_name") String displayName) {
    }
}

package com.microfi.transactions.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

    public GeocodingService(WebClient.Builder webClientBuilder, @Value("${geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl) {
        // Nominatim's usage policy requires an identifying User-Agent on every request.
        this.webClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, "MICROFI-Core/1.0 (field cash-collection app)")
                .build();
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
                    .block();
            return response != null ? response.displayName() : null;
        } catch (Exception e) {
            log.warn("Reverse geocoding failed for ({}, {}): {}", lat, lon, e.getMessage());
            return null;
        }
    }

    private record NominatimResponse(@JsonProperty("display_name") String displayName) {
    }
}

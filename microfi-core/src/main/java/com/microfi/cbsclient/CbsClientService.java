package com.microfi.cbsclient;

import com.microfi.shared.dto.MiddlewareExportAck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * cbsclient — Core's only outbound path to the CBS Middleware (never calls the CBS directly).
 * Uses WebClient per the doc's tech stack ("HTTP client to MW | Spring WebClient"); this is the
 * one core module documented as calling the Middleware over REST rather than via RabbitMQ.
 */
@Service
@Slf4j
public class CbsClientService {

    private final WebClient webClient;

    public CbsClientService(WebClient.Builder webClientBuilder, @Value("${middleware.base-url}") String middlewareBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(middlewareBaseUrl).build();
    }

    /** FR-18: submits the daily CBS export file reference to the middleware for posting. */
    public Mono<MiddlewareExportAck> submitDailyExport(UUID branchId, String fileUri, String format) {
        return webClient.post()
                .uri("/mw/v1/exports/daily")
                .bodyValue(Map.of(
                        "branchId", branchId.toString(),
                        "fileUri", fileUri,
                        "format", format))
                .retrieve()
                .bodyToMono(MiddlewareExportAck.class)
                .doOnError(e -> log.error("Middleware daily export submission failed for branch {}: {}", branchId, e.getMessage()));
    }
}

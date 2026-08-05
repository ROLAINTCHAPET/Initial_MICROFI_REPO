package com.microfi.cbsclient;

import com.microfi.shared.dto.MiddlewareBalance;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.MiddlewareFeeSplit;
import com.microfi.shared.dto.MiddlewareHistoryEntry;
import com.microfi.shared.dto.MiddlewareMemberVerification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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
    

    /** UC-19: validates a CBS Activation ID and resolves the member it belongs to. */
    public Mono<MiddlewareMemberVerification> verifyMember(String activationId) {
        return webClient.post()
                .uri("/mw/v1/members/verify")
                .bodyValue(Map.of("activationId", activationId))
                .retrieve()
                .bodyToMono(MiddlewareMemberVerification.class)
                .doOnError(e -> log.error("Middleware member verification failed: {}", e.getMessage()));
    }

    /** FR-19: splits an activation fee between the sponsoring agent and the MFI. */
    public Mono<MiddlewareFeeSplit> splitFee(String memberId, String agentId, long amountXaf, String idempotencyKey) {
        return webClient.post()
                .uri("/mw/v1/fees/split")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(Map.of("memberId", memberId, "agentId", agentId, "amountXaf", amountXaf))
                .retrieve()
                .bodyToMono(MiddlewareFeeSplit.class)
                .doOnError(e -> log.error("Middleware fee split failed for member {}: {}", memberId, e.getMessage()));
    }

    /** FR-21: live balance from the CBS. */
    public Mono<MiddlewareBalance> getBalance(String memberId) {
        return webClient.post()
                .uri("/mw/v1/members/balance")
                .bodyValue(Map.of("memberId", memberId))
                .retrieve()
                .bodyToMono(MiddlewareBalance.class)
                .doOnError(e -> log.error("Middleware balance lookup failed for member {}: {}", memberId, e.getMessage()));
    }

    /** FR-22: contribution history from the CBS. */
    public Flux<MiddlewareHistoryEntry> getHistory(String memberId) {
        return webClient.get()
                .uri("/mw/v1/members/{id}/history", memberId)
                .retrieve()
                .bodyToFlux(MiddlewareHistoryEntry.class)
                .doOnError(e -> log.error("Middleware history lookup failed for member {}: {}", memberId, e.getMessage()));
    }
}

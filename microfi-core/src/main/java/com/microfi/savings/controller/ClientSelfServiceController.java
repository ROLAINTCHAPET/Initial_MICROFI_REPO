package com.microfi.savings.controller;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.service.ClientSelfService;
import com.microfi.shared.dto.ClientBalanceResponse;
import com.microfi.shared.dto.ClientHistoryEntryResponse;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-20/21/22 — My Profile / My Account / My Contribution History. Always read-only; per FR-20/21/22
 * none of these are blocked by an expired booklet token, so there is no token-status gate here —
 * only that the caller is an authenticated client.
 */
@RestController
@RequestMapping("/api/v1/clients/me")
@RequiredArgsConstructor
@Tag(name = "Client Self-Service", description = "Client profile, balance and contribution history (read-only)")
public class ClientSelfServiceController {

    private final ClientSelfService clientSelfService;
    private final CbsClientService cbsClientService;

    @GetMapping("/profile")
    @Operation(summary = "My Profile", description = "Identity and digital booklet token status (UC-20). Client cannot edit identity attributes.")
    public Mono<ClientProfileSelfResponse> profile(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getProfile(clientId))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/balance")
    @Operation(summary = "My Account Balance", description = "Live balance from the CBS (UC-21). Visible even if the booklet token has expired.")
    public Mono<ClientBalanceResponse> balance(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getCbsRef(clientId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(cbsClientService::getBalance)
                .map(balance -> ClientBalanceResponse.builder()
                        .balanceXaf(balance.getBalanceXaf())
                        .asOf(balance.getAsOf())
                        .build());
    }

    @GetMapping("/history")
    @Operation(summary = "My Contribution History", description = "Mini-statement replacing paper booklet pages (UC-22). Append-only from the client's perspective.")
    public Flux<ClientHistoryEntryResponse> history(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getCbsRef(clientId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(cbsClientService::getHistory)
                .map(entry -> ClientHistoryEntryResponse.builder()
                        .reference(entry.getReference())
                        .amountXaf(entry.getAmountXaf())
                        .date(entry.getDate())
                        .type(entry.getType())
                        .build());
    }

    private Mono<UUID> resolveClientId(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> ((ClientDetails) authentication.getPrincipal()).getClient().getId());
    }
}

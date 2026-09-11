package com.microfi.transactions.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.shared.dto.OfjClosingExportAlertResponse;
import com.microfi.transactions.service.OfjClosingExportAlertBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Real-time Back-Office notification that the scheduled closing-time job (see
 * {@code OfjClosingTimeExportJob}) has posted a branch's confirmed cash to the CBS, whether or not
 * every agent had already tapped "End My Day" — the user-requested audience is deliberately wider
 * than the SOS/collection-rejection precedent: ADMIN, BRANCH_MANAGER, and BRANCH_CASHIER all see
 * their own branch's export.
 */
@RestController
@RequestMapping("/api/v1/admin/ofj-export-alerts")
@RequiredArgsConstructor
@Tag(name = "OFJ Export Alerts", description = "Real-time push of scheduled closing-time OFJ export completions")
public class OfjExportAlertController {

    private final OfjClosingExportAlertBroadcaster ofjClosingExportAlertBroadcaster;

    /** Comment-only, no data — purely to keep the connection past Kong's ~60s idle-read timeout, same as CollectionRejectionController's stream. */
    private static final Flux<ServerSentEvent<OfjClosingExportAlertResponse>> HEARTBEAT =
            Flux.interval(Duration.ofSeconds(20)).map(tick -> ServerSentEvent.<OfjClosingExportAlertResponse>builder().comment("keep-alive").build());

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream OFJ Export Alerts", description = "Server-Sent Events push of scheduled closing-time export completions, for an instant notification instead of waiting on the next page load. Branch scope resolved per event. ADMIN, BRANCH_MANAGER, or BRANCH_CASHIER.")
    public Flux<ServerSentEvent<OfjClosingExportAlertResponse>> stream(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER, AdminRole.BRANCH_CASHIER)
                .flatMapMany(caller -> ofjClosingExportAlertBroadcaster.stream()
                        .filter(alert -> caller.getAdminUser().getRole() == AdminRole.ADMIN
                                || Objects.equals(caller.getAdminUser().getBranchId(), alert.getBranchId()))
                        .map(alert -> ServerSentEvent.builder(alert).build()))
                .mergeWith(HEARTBEAT);
    }
}

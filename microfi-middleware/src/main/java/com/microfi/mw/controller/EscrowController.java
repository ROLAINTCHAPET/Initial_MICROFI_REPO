package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.EscrowCreditResult;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.EscrowCreditRequest;
import com.microfi.mw.service.CbsIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mw/v1/escrow")
@RequiredArgsConstructor
@Tag(name = "Escrow", description = "Agent escrow credit in the CBS (internal, called by Core only)")
public class EscrowController {

    private final CbsIntegrationService cbsIntegrationService;

    @PostMapping("/credit")
    @Operation(summary = "Credit an agent's escrow balance in the CBS",
            description = "Idempotent when an Idempotency-Key header is supplied.")
    EscrowCreditResult credit(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                               @Valid @RequestBody EscrowCreditRequest request) {
        return cbsIntegrationService.creditEscrow(CorrelationId.resolve(correlationId), idempotencyKey,
                request.agentId(), request.amountXaf(), request.reference());
    }
}

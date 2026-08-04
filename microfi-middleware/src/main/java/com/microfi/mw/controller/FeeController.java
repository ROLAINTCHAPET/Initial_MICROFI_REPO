package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.FeeSplitResult;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.FeeSplitRequest;
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
@RequestMapping("/mw/v1/fees")
@RequiredArgsConstructor
@Tag(name = "Fees", description = "Client activation fee split between agent and MFI (internal, called by Core only)")
public class FeeController {

    private final CbsIntegrationService cbsIntegrationService;

    @PostMapping("/split")
    @Operation(summary = "Split an activation fee into agent commission and MFI share",
            description = "Idempotent when an Idempotency-Key header is supplied.")
    FeeSplitResult split(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                          @Valid @RequestBody FeeSplitRequest request) {
        return cbsIntegrationService.splitFee(CorrelationId.resolve(correlationId), idempotencyKey,
                request.memberId(), request.agentId(), request.amountXaf());
    }
}

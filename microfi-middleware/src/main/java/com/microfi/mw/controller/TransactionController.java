package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.TransactionPostResult;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.PostTransactionsRequest;
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
@RequestMapping("/mw/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Posting of collections to the CBS ledger (internal, called by Core only)")
public class TransactionController {

    private final CbsIntegrationService cbsIntegrationService;

    @PostMapping("/post")
    @Operation(summary = "Post one or more collections to the CBS ledger",
            description = "Idempotent when an Idempotency-Key header is supplied: duplicate deliveries never double-post to the CBS.")
    TransactionPostResult post(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                @Valid @RequestBody PostTransactionsRequest request) {
        return cbsIntegrationService.postTransactions(CorrelationId.resolve(correlationId), idempotencyKey, request.collections());
    }
}

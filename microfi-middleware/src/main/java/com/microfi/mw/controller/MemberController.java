package com.microfi.mw.controller;

import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.api.CorrelationId;
import com.microfi.mw.api.dto.BalanceRequest;
import com.microfi.mw.api.dto.VerifyMemberRequest;
import com.microfi.mw.service.CbsIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mw/v1/members")
@RequiredArgsConstructor
@Tag(name = "Members", description = "CBS member verification, balance and history (internal, called by Core only)")
public class MemberController {

    private final CbsIntegrationService cbsIntegrationService;

    @PostMapping("/verify")
    @Operation(summary = "Validate an Activation ID / member against the CBS")
    MemberVerificationResult verify(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                     @Valid @RequestBody VerifyMemberRequest request) {
        return cbsIntegrationService.verifyMember(CorrelationId.resolve(correlationId), request.activationId());
    }

    @PostMapping("/balance")
    @Operation(summary = "Fetch a member's live balance from the CBS")
    BalanceResult balance(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                           @Valid @RequestBody BalanceRequest request) {
        return cbsIntegrationService.getBalance(CorrelationId.resolve(correlationId), request.memberId());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Pull member contribution history from the CBS when the local cache is stale")
    List<HistoryEntry> history(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                @PathVariable("id") String memberId) {
        return cbsIntegrationService.getHistory(CorrelationId.resolve(correlationId), memberId);
    }
}
